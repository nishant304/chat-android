package com.smart.rchat.smart;

import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.cache.DiskLruCacheFactory;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.smart.rchat.smart.adapter.ChatRoomAdapter;
import com.smart.rchat.smart.database.RChatContract;
import com.smart.rchat.smart.util.AppData;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import hani.momanii.supernova_emoji_library.Actions.EmojIconActions;
import hani.momanii.supernova_emoji_library.Helper.EmojiconEditText;

import static android.provider.MediaStore.ACTION_IMAGE_CAPTURE;

/**
 * Created by nishant on 28.01.17.
 */

public class ChatRoomActivity extends  BaseActivity implements View.OnClickListener,
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


    public static  final int TYPE_MESSAGE = 1;
    public static  final int TYPE_IMAGE = 2;

    private static  final int REQUEST_IMAGE_CAPTURE = 1;

    FirebaseUser currUser = FirebaseAuth.getInstance().getCurrentUser();
    private String friendUserId;
    private String name;

    private ChatRoomAdapter chatRoomAdapter ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_chat_room);
        friendUserId = getIntent().getStringExtra("friend_user_id");
        name = getIntent().getStringExtra("name");
        ButterKnife.bind(this);
        send.setOnClickListener(this);
        View rootView = findViewById(R.id.rootView);
        EmojIconActions emojIcon=new EmojIconActions(this,rootView,edMessageBox,emoji);
        emojIcon.ShowEmojIcon();

        setupListView();

        edMessageBox.setOnTouchListener(this);

        getLoaderManager().initLoader(0,null,this);

        TextView tvName = (TextView) toolbar.findViewById(R.id.tbName);
        tvName.setText(name);

        final  TextView tvLastSeen = (TextView) toolbar.findViewById(R.id.tbLastSeen);
        FirebaseDatabase.getInstance().getReference().child("Users").child(friendUserId).child("status").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.getValue()!=null) {
                    tvLastSeen.setText(dataSnapshot.getValue().toString());
                }else{
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
    protected void onResume() {
        super.onResume();
        FirebaseDatabase.getInstance().getReference().child("Users").
                child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child("status").setValue("Online");
    }

    private void setupListView(){
        chatRoomAdapter =  new ChatRoomAdapter(this,null,friendUserId);
        listView.setAdapter(chatRoomAdapter);
        listView.setDivider(null);
        listView.setStackFromBottom(true);
        listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
    }

    @Override
    public void onClick(View v) {
        if(edMessageBox.getText().toString().equals("")){
            return;
        }
        String message = edMessageBox.getText().toString();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference().child("/Messages");
        HashMap<String,Object> hashMap = new HashMap<>();
        hashMap.put("from",currUser.getUid());
        hashMap.put("to",friendUserId);
        hashMap.put("message",message);
        hashMap.put("type",TYPE_MESSAGE);
        ContentValues cv = new ContentValues();
        cv.put(RChatContract.MESSAGE_TABLE.to,friendUserId);
        cv.put(RChatContract.MESSAGE_TABLE.message,hashMap.get("message").toString());
        cv.put(RChatContract.MESSAGE_TABLE.time,System.currentTimeMillis());
        cv.put(RChatContract.MESSAGE_TABLE.from,currUser.getUid());
        cv.put(RChatContract.MESSAGE_TABLE.type,TYPE_MESSAGE);
        getContentResolver().insert(RChatContract.MESSAGE_TABLE.CONTENT_URI,cv);
        ref.push().setValue(hashMap).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                makeToast("success sending");
            }
        });
        edMessageBox.getText().clear();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if(event.getAction() == MotionEvent.ACTION_UP) {
            if(event.getRawX() >= (edMessageBox.getRight() - edMessageBox.getCompoundDrawables()[2].
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
        return new CursorLoader(this,RChatContract.MESSAGE_TABLE.CONTENT_URI,null, RChatContract.MESSAGE_TABLE.from
             +" =? OR "+ RChatContract.MESSAGE_TABLE.to + " =? ",new String[]{friendUserId,friendUserId},null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        chatRoomAdapter.swapCursor(data);
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
            String fileUrl = "images/" + UUID.randomUUID()+".png";
            AppData.getInstance().getLruCache().put(fileUrl,imageBitmap);
            ContentValues cv = new ContentValues();
            cv.put(RChatContract.MESSAGE_TABLE.to,friendUserId);
            cv.put(RChatContract.MESSAGE_TABLE.message,fileUrl);
            cv.put(RChatContract.MESSAGE_TABLE.time,System.currentTimeMillis());
            cv.put(RChatContract.MESSAGE_TABLE.from,currUser.getUid());
            cv.put(RChatContract.MESSAGE_TABLE.type,TYPE_IMAGE);
            getContentResolver().insert(RChatContract.MESSAGE_TABLE.CONTENT_URI,cv);
        }
    }
}
