package com.smart.rchat.smart.services;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.smart.rchat.smart.ChatRoomActivity;
import com.smart.rchat.smart.IActivityCallBack;
import com.smart.rchat.smart.IContactListener;
import com.smart.rchat.smart.R;
import com.smart.rchat.smart.database.RChatContract;
import com.smart.rchat.smart.util.AppUtil;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by nishant on 29.01.17.
 */

public class ContactsListenerService extends Service {

    private IActivityCallBack iActivityCallBack;

    private HashMap<String, String> idToName = new HashMap<>();

    private HashMap<String, Boolean> groupId = new HashMap<>();

    private boolean stopTask = false;

    @Override
    public void onCreate() {
        super.onCreate();
        getApplicationContext().getContentResolver().registerContentObserver
                (ContactsContract.CommonDataKinds.Phone.CONTENT_URI, false, new ContactsObserver());
        stopTask = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        FirebaseApp.initializeApp(this);
        groupId = new HashMap<>();
        updateDb();
        if (intent != null) { // hack for duplicate messages on service restart
            listenForMessages(AppUtil.getUserId());
            listenForGroupMessages();
        }
        return START_STICKY;
    }

    private class ContactsObserver extends ContentObserver {

        public ContactsObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateDb();
        }
    }

    private void updateDb() {
        Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[]
                {ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null);
        List<String> phoneNumbers = new ArrayList<>();
        while (cursor.moveToNext()) {
            String number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            Pattern p = Pattern.compile("[^a-z0-9 ]", Pattern.CASE_INSENSITIVE);
            number = number.trim().replace(" ", "").replace("-", "").replace("+", "");
            if (p.matcher(number).find()) {
                continue;
            }
            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            checkPhone(number, name);
        }
        cursor.close();
    }

    private void checkPhone(final String phoneNo, final String name) {
        FirebaseDatabase.getInstance().getReference().child("/Phone").child(phoneNo).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String userID = null;
                if (dataSnapshot.exists()) {
                    userID = dataSnapshot.getValue().toString();
                    if (userID != null) {
                        idToName.put(userID, name);
                    }
                }

                if (userID != null && !userID.equals(AppUtil.getUserId())) {
                    final String finalUserID = userID;

                    FirebaseDatabase.getInstance().getReference().child("/Users").child(userID).addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            HashMap<String, Object> map = (HashMap<String, Object>) dataSnapshot.getValue();
                            ContentValues contentValues = new ContentValues();
                            contentValues.put(RChatContract.USER_TABLE.USER_ID, finalUserID);
                            contentValues.put(RChatContract.USER_TABLE.USER_NAME, name);
                            contentValues.put(RChatContract.USER_TABLE.PHONE, phoneNo);
                            contentValues.put(RChatContract.USER_TABLE.PROFILE_PIC, "");
                            contentValues.put(RChatContract.USER_TABLE.type, 1);
                            getContentResolver().insert(RChatContract.USER_TABLE.CONTENT_URI, contentValues);
                            idToName.put(finalUserID, name);
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {

        if (!stopTask) {

            Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
            restartServiceIntent.setPackage(getPackageName());

            PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);
            AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(ALARM_SERVICE);
            alarmService.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 1000,
                    restartServicePendingIntent);
        }

        super.onTaskRemoved(rootIntent);
    }

    private void listenForMessages(final String userId) {

        if (userId == null) {
            return;
        }

        FirebaseDatabase.getInstance().getReference().child("Messages").orderByChild("to")
                .equalTo(userId).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String ss) {

                HashMap<String, Object> hm = (HashMap<String, Object>) dataSnapshot.getValue();
                if (hm == null) {
                    return;
                }
                int type = Integer.valueOf(hm.get("type").toString());
                if (type == 4) {
                    handleGroupRequest(hm);
                    return;
                }
                Log.d("nishant", "" + hm.get("message").toString() + " from " + hm.get("from").toString());
                ContentValues cv = new ContentValues();
                cv.put(RChatContract.MESSAGE_TABLE.from, hm.get("from").toString());
                cv.put(RChatContract.MESSAGE_TABLE.type, type);
                cv.put(RChatContract.MESSAGE_TABLE.message, hm.get("message").toString());
                cv.put(RChatContract.MESSAGE_TABLE.time, System.currentTimeMillis());
                cv.put(RChatContract.MESSAGE_TABLE.to, userId);
                cv.put(RChatContract.MESSAGE_TABLE.msg_id, dataSnapshot.getKey());
                Uri uri = getContentResolver().insert(RChatContract.MESSAGE_TABLE.CONTENT_URI, cv);
                try {
                    if (uri != null && iActivityCallBack == null || !iActivityCallBack.getFriendIdInChat().equals(hm.get("from").toString())) {
                        createNotification(hm.get("from").toString(), hm.get("message").toString(), false);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    private void listenForGroupMessages() {
        String userId = AppUtil.getUserId();
        if (userId == null) {
            return;
        }
        FirebaseDatabase.getInstance().getReference().child("Users").child(userId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                HashMap<String, Object> map = (HashMap<String, Object>) dataSnapshot.getValue();
                ArrayList<String> groupList = (ArrayList<String>) map.get("groups");
                if (groupList == null) {
                    return;
                }

                for (String groupId : groupList) {
                    listenForMessages(groupId);
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void createNotification(String friendUserId, String message, boolean isGroupReq) {
        Intent resultIntent = new Intent(this, ChatRoomActivity.class);
        resultIntent.putExtra("friend_user_id", friendUserId);
        resultIntent.putExtra("name", idToName.get(friendUserId) != null ? idToName.get(friendUserId) : "");
        resultIntent.putExtra("isGroupReq", isGroupReq);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        android.support.v4.app.NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this).
                setContentTitle(idToName.get(friendUserId))
                .setContentText(message)
                .setSmallIcon(R.drawable.zzz_alert)
                .setContentIntent(resultPendingIntent);

        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.notify(001, mBuilder.build());
    }

    private void handleGroupRequest(HashMap<String, Object> map) {
        //Fixme names
        String message = map.get("message").toString();

        try {
            final JSONObject jsonObject = new JSONObject(message);
            if (groupId.get(jsonObject.getString("groupId")) != null) {  //hack ,Fix this
                return;
            }

            groupId.put(jsonObject.getString("groupId"), true);
            ContentValues contentValues = new ContentValues();
            contentValues.put(RChatContract.USER_TABLE.USER_ID, jsonObject.getString("groupId"));
            contentValues.put(RChatContract.USER_TABLE.USER_NAME, jsonObject.getString("name"));
            contentValues.put(RChatContract.USER_TABLE.PROFILE_PIC, jsonObject.getString("url"));
            contentValues.put(RChatContract.USER_TABLE.memebers, jsonObject.getJSONArray("members").toString());
            contentValues.put(RChatContract.USER_TABLE.type, 2);
            getContentResolver().insert(RChatContract.USER_TABLE.CONTENT_URI, contentValues);
            createNotification(jsonObject.getString("groupId"), "new group request", true);
            FirebaseDatabase.getInstance().getReference().child("Users").child(FirebaseAuth.
                    getInstance().getCurrentUser().getUid()).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    try {
                        HashMap<String, Object> map = (HashMap<String, Object>) dataSnapshot.getValue();
                        ArrayList<String> list = (ArrayList<String>) map.get("groups");
                        if (list == null) {
                            list = new ArrayList<String>();
                        }
                        if (itemExists(list, jsonObject.getString("groupId"))) {
                            return;
                        }
                        list.add(jsonObject.getString("groupId"));
                        map.put("groups", list);
                        dataSnapshot.getRef().removeEventListener(this);
                        dataSnapshot.getRef().setValue(map).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                try {
                                    listenForMessages(jsonObject.getString("groupId"));
                                } catch (Exception e) {

                                }
                            }
                        });
                    } catch (Exception e) {

                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });

        } catch (Exception e) {

        }
    }

    private boolean itemExists(ArrayList<String> list, String item) {
        for (String items : list) {
            if (items.equals(item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    IContactListener.Stub binder = new IContactListener.Stub() {
        @Override
        public void registerActivityCallBack(IActivityCallBack callBack) throws RemoteException {
            iActivityCallBack = callBack;
        }

        @Override
        public void stopService() throws RemoteException {

            FirebaseAuth.getInstance().signOut();
            stopTask = true;
            stopSelf();
            stopService(); //rec stack overflow, fix problems I dnt know
        }
    };

}
