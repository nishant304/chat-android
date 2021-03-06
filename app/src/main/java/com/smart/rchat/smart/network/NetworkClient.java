package com.smart.rchat.smart.network;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.widget.ImageView;

import com.google.firebase.auth.FirebaseAuth;
import com.smart.rchat.smart.interfaces.IServerEndPoint;
import com.smart.rchat.smart.interfaces.ResponseListener;
import com.smart.rchat.smart.models.MessageRequest;
import com.smart.rchat.smart.util.AppData;

/**
 * Created by nishant on 05.02.17.
 */

public class NetworkClient {

    private static NetworkClient sInstance = new NetworkClient();

    IServerEndPoint serverEndPoint;

    private NetworkClient(){
        serverEndPoint = new FireBaseImpl();
    }

    public static NetworkClient getInstance(){
        if(Looper.getMainLooper() != Looper.myLooper()){
            throw new IllegalStateException("please use main thread");
        }
        return sInstance;
    }

    public String sendMessage(String to,String message){
      return  serverEndPoint.sendMessage(new MessageRequest(to,
                FirebaseAuth.getInstance().getCurrentUser().getUid(),message,1));
    }

    public String sendImageRequest(String to,String url){
       return serverEndPoint.sendMessage(new MessageRequest(to,
                FirebaseAuth.getInstance().getCurrentUser().getUid(),url,2));
    }

    public String sendContactRequest(String to,String url){
        return serverEndPoint.sendMessage(new MessageRequest(to,
                FirebaseAuth.getInstance().getCurrentUser().getUid(),url,3));
    }

    public  void loginUser(String email, String password, ResponseListener responseListener){
        serverEndPoint.loginUser(email,password,responseListener);
    }

    public  void createUser(String email, String password, ResponseListener responseListener){
        serverEndPoint.createUser(email,password,responseListener);
    }

    public  void updateStatus(String status){
        serverEndPoint.updateUserStatus(status);
    }

    public  void uploadBitMap(String url,ResponseListener responseListener){
        serverEndPoint.uploadPhoto(url, AppData.getInstance().getLruCache().get(url),responseListener);
    }

    public  void createGroup(String gr,Bitmap bitmap, String[] user,ResponseListener responseListener){
        serverEndPoint.createGroup(gr,bitmap,user,responseListener);
    }

    public  void loadBitMap(Context context,String url, ImageView imageView,int type){
        serverEndPoint.loadBitMap(context,url,imageView,type);
    }

}
