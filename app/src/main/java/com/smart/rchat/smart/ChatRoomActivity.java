package com.smart.rchat.smart;

import android.app.LoaderManager;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smart.rchat.smart.adapter.ChatRoomAdapter;
import com.smart.rchat.smart.adapter.GroupChatRoomAdapter;
import com.smart.rchat.smart.database.RChatContract;
import com.smart.rchat.smart.interfaces.ResponseListener;
import com.smart.rchat.smart.models.User;
import com.smart.rchat.smart.network.NetworkClient;
import com.smart.rchat.smart.util.AppData;
import com.smart.rchat.smart.util.AppUtil;
import com.vstechlab.easyfonts.EasyFonts;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;

import butterknife.BindView;
import hani.momanii.supernova_emoji_library.Actions.EmojIconActions;
import hani.momanii.supernova_emoji_library.Helper.EmojiconEditText;

import static android.provider.MediaStore.ACTION_IMAGE_CAPTURE;

/**
 * Created by nishant on 28.01.17.
 */

public class ChatRoomActivity extends BaseActivity implements View.OnClickListener,
        LoaderManager.LoaderCallbacks<Cursor>, View.OnTouchListener {

    @BindView(R.id.toolbar3)
    public Toolbar toolbar;

    @BindView(R.id.btSendMessage)
    public ImageView send;

    @BindView(R.id.edMessageBox)
    public EmojiconEditText edMessageBox;

    @BindView(R.id.lvChatRoom)
    public ListView listView;

    @BindView(R.id.smiley)
    public ImageView emoji;

    private JSONArray members;

    public static final int TYPE_MESSAGE = 1;
    public static final int TYPE_IMAGE = 2;

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int PICK_IMAGE = 2;
    private static final int PICK_CONTACT = 3;

    int type;

    FirebaseUser currUser = FirebaseAuth.getInstance().getCurrentUser();
    private String friendUserId;
    private String name;

    private ChatRoomAdapter chatRoomAdapter;

    private TextView tvLastSeen;

    private ArrayList<User> users = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_chat_room);
        friendUserId = getIntent().getStringExtra("friend_user_id");
        name = getIntent().getStringExtra("name");
        type = getIntent().getIntExtra("type", 0);

        send.setOnClickListener(this);
        edMessageBox.setTypeface(EasyFonts.robotoMedium(this));
        View rootView = findViewById(R.id.rootView);
        EmojIconActions emojIcon = new EmojIconActions(this, rootView, edMessageBox, emoji);
        emojIcon.ShowEmojIcon();

        setupListView();
        edMessageBox.setOnTouchListener(this);
        getLoaderManager().initLoader(0, null, this);
        if (type == 2) {
            getLoaderManager().initLoader(1, null, this);
        }
        setUpToolBar();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void setupListView() {
        if(type == 1) {
            chatRoomAdapter = new ChatRoomAdapter(this, null, friendUserId);
        }else{
            chatRoomAdapter = new GroupChatRoomAdapter(this, null, friendUserId);
        }
        listView.setAdapter(chatRoomAdapter);
        listView.setDivider(null);
        listView.setStackFromBottom(true);
        listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
    }

    private void setUpToolBar() {
        TextView tvName = (TextView) toolbar.findViewById(R.id.tbName);
        tvName.setText(name);
        tvName.setOnClickListener(this);

        ImageView im = (ImageView) toolbar.findViewById(R.id.profile);
        getNetworkClient().loadBitMap(this, friendUserId, im, type);

        tvLastSeen = (TextView) toolbar.findViewById(R.id.tbLastSeen);
        tvLastSeen.setOnClickListener(this);
        FirebaseDatabase.getInstance().getReference().child("Users").child(friendUserId).child("status").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    tvLastSeen.setText(dataSnapshot.getValue().toString());
                } else {
                    tvLastSeen.setText("");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                tvLastSeen.setText("");
            }
        });

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayUseLogoEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.tbName) {
            openProfile();
            return;
        }

        if (v.getId() == R.id.tbLastSeen) {
            openProfile();
            return;
        }

        if (edMessageBox.getText().toString().equals("")) {
            return;
        }

        String message = edMessageBox.getText().toString();
        String key = getNetworkClient().sendMessage(friendUserId, message);
        getContentResolver().insert(RChatContract.MESSAGE_TABLE.CONTENT_URI, AppUtil.
                getCVforMessafRequest(friendUserId, message, 1, key));
        edMessageBox.getText().clear();
    }

    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("id", friendUserId);
        intent.putExtra("type", type);
        intent.putExtra("name", name);
        if(type == 2){
            AppData.getInstance().dumpObject(users);
        }
        startActivity(intent);
    }

    /***
     * use the touch position to detect touch on camera icon
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (event.getRawX() >= (edMessageBox.getRight() - edMessageBox.getCompoundDrawables()[2].
                    getBounds().width())) {
                Intent takePictureIntent = new Intent(ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        if (type == 1) {

            return new CursorLoader(this, RChatContract.MESSAGE_TABLE.CONTENT_URI, null,
                    RChatContract.MESSAGE_TABLE.from + " =? AND " + RChatContract.MESSAGE_TABLE.to + " =? OR " +
                            RChatContract.MESSAGE_TABLE.from + " =? AND " + RChatContract.MESSAGE_TABLE.to + " =? ",
                    new String[]{friendUserId, AppUtil.getUserId(), AppUtil.getUserId(), friendUserId}, null);
        }

        if (id == 0) {

            return new CursorLoader(this, RChatContract.MESSAGE_TABLE.CONTENT_URI, null,
                    RChatContract.MESSAGE_TABLE.from + " =? OR " +
                            RChatContract.MESSAGE_TABLE.to + " =? ",
                    new String[]{friendUserId, friendUserId}, null);
        }

        return getLoaderForGroupMembers();
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == 0) {
            chatRoomAdapter.swapCursor(data);
        } else {
            try {
                onGroupMemberInfoFetched(data);
            }catch (JSONException js){

            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        //Fixme
        //NavUtils.shouldUpRecreateTask(this,new Intent(this,HomeActivity.class));
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        chatRoomAdapter.swapCursor(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            final String fileUrl = "images/" + UUID.randomUUID() + ".png";
            AppData.getInstance().getLruCache().put(fileUrl, imageBitmap);

            NetworkClient.getInstance().uploadBitMap(fileUrl, new ResponseListener() {
                @Override
                public void onSuccess(JSONObject jsonObject) {
                    String key = NetworkClient.getInstance().sendImageRequest(friendUserId, fileUrl);
                    ContentValues cv = new ContentValues();
                    cv.put(RChatContract.MESSAGE_TABLE.to, friendUserId);
                    cv.put(RChatContract.MESSAGE_TABLE.message, fileUrl);
                    cv.put(RChatContract.MESSAGE_TABLE.time, System.currentTimeMillis());
                    cv.put(RChatContract.MESSAGE_TABLE.from, currUser.getUid());
                    cv.put(RChatContract.MESSAGE_TABLE.type, TYPE_IMAGE);
                    cv.put(RChatContract.MESSAGE_TABLE.msg_id, key);
                    getContentResolver().insert(RChatContract.MESSAGE_TABLE.CONTENT_URI, cv);
                }

                @Override
                public void onError(Exception error) {

                }
            });
        }
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = AppUtil.getBitmapFromUri(data.getData(), this);
            final String fileUrl = "images/" + UUID.randomUUID() + ".png";
            AppData.getInstance().getLruCache().put(fileUrl, imageBitmap);

            NetworkClient.getInstance().uploadBitMap(fileUrl, new ResponseListener() {
                @Override
                public void onSuccess(JSONObject jsonObject) {
                    String key = NetworkClient.getInstance().sendImageRequest(friendUserId, fileUrl);
                    ContentValues cv = new ContentValues();
                    cv.put(RChatContract.MESSAGE_TABLE.to, friendUserId);
                    cv.put(RChatContract.MESSAGE_TABLE.message, fileUrl);
                    cv.put(RChatContract.MESSAGE_TABLE.time, System.currentTimeMillis());
                    cv.put(RChatContract.MESSAGE_TABLE.from, currUser.getUid());
                    cv.put(RChatContract.MESSAGE_TABLE.type, TYPE_IMAGE);
                    cv.put(RChatContract.MESSAGE_TABLE.msg_id, key);
                    getContentResolver().insert(RChatContract.MESSAGE_TABLE.CONTENT_URI, cv);
                }

                @Override
                public void onError(Exception error) {

                }
            });
        }

        if (requestCode == PICK_CONTACT && resultCode == RESULT_OK) {
            Uri uri = data.getData();

            AsyncQueryHandler as = new AsyncQueryHandler(getContentResolver()) {
                @Override
                protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                    try {
                        while (cursor.moveToNext()) {
                            JSONObject message = new JSONObject();

                            String number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                            Pattern p = Pattern.compile("[^a-z0-9 ]", Pattern.CASE_INSENSITIVE);
                            number = number.trim().replace(" ", "").replace("-", "").replace("+", "");
                            if (p.matcher(number).find()) {
                                continue;
                            }
                            message.put("number", number);
                            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                            message.put("name", name);

                            String key = getNetworkClient().sendContactRequest(friendUserId, message.toString());
                            getContentResolver().insert(RChatContract.MESSAGE_TABLE.CONTENT_URI, AppUtil.
                                    getCVforMessafRequest(friendUserId, message.toString(), 3, key));
                        }
                        cursor.close();
                    } catch (Exception e) {

                    }
                }
            };
            as.startQuery(1, null, uri, new String[]
                    {ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null);

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_screen_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.action_send_photo) {
            Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
            getIntent.setType("image/*");
            getIntent.putExtra("return-data", true);
            Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickIntent.setType("image/*");
            pickIntent.putExtra("return-data", true);
            Intent chooserIntent = Intent.createChooser(getIntent, "Select Image");
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{pickIntent});
            startActivityForResult(chooserIntent, PICK_IMAGE);
            return true;
        } else if (item.getItemId() == R.id.action_send_contact) {

            Intent contactPickerIntent = new Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(contactPickerIntent, PICK_CONTACT);
            return true;
        }

        return false;
    }

    private CursorLoader getLoaderForGroupMembers() {
        String members = getIntent().getStringExtra("members");
        try {
            this.members = new JSONArray(members);
            return new CursorLoader(this, RChatContract.USER_TABLE.CONTENT_URI, null,
                    AppUtil.getSelection(this.members),
                    null, null);
        } catch (JSONException e) {

        }
        return null;
    }

    private void onGroupMemberInfoFetched(Cursor cursor) throws JSONException {
        StringBuilder stringBuilder = new StringBuilder();
        cursor.moveToFirst();
        HashMap<String,String> hm = new HashMap<>();
        users = new ArrayList<>();
        while (!cursor.isAfterLast()){
            String id = cursor.getString(cursor.getColumnIndex(RChatContract.USER_TABLE.USER_ID));
            String  name = cursor.getString(cursor.getColumnIndex(RChatContract.USER_TABLE.USER_NAME));
            String  number = cursor.getString(cursor.getColumnIndex(RChatContract.USER_TABLE.PHONE));
            String url = cursor.getString(cursor.getColumnIndex(RChatContract.USER_TABLE.PROFILE_PIC));
            hm.put(id,name);
            users.add(new User(id,url,number,name));
            stringBuilder.append(name+ ", ");
            cursor.moveToNext();
        }
        if(stringBuilder.length() != 0) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        updateToolBar(stringBuilder.toString());

        for(int i=0;i<members.length();i++){
            if(hm.get(members.get(i)) == null){
               users.add(new User((String) members.get(i),"","",""));
            }
        }


        //AppData.getInstance().dumpObject(users);

        //do we need to wait for this cursorload??
        chatRoomAdapter.setUserNameMapping(hm);

    }

    private  void updateToolBar(String users){
        tvLastSeen.setText(users);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppData.getInstance().getDumpObject();// clean
    }
}
