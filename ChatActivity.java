package com.example.chatapp;
//finalllly updated one
import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.*;
import com.example.chatapp.databinding.ActivityChatBinding;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG="CHAT_TAG";
    private ActivityChatBinding binding;
    private FirebaseAuth firebaseAuth;
    private ProgressDialog progressDialog;
    private String receiptUid,myUid,chatPath,myName,receiptFcmToken;
    private Uri imageUri;
    private ArrayList<ModelChat> chatArrayList=new ArrayList<>();

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        binding=ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        firebaseAuth=FirebaseAuth.getInstance();

        progressDialog=new ProgressDialog(this);
        progressDialog.setTitle("Please wait");
        progressDialog.setCanceledOnTouchOutside(false);

        receiptUid=getIntent().getStringExtra("receiptUid");
        myUid=firebaseAuth.getUid();
        chatPath=Utils.chatPath(receiptUid,myUid);

        Log.d(TAG,"onCreate: receiptUid:"+receiptUid);
        Log.d(TAG,"onCreate: myUid:"+myUid);
        Log.d(TAG,"onCreate: chatPath:"+chatPath);

        loadMyInfo();
        loadReceiptDetails();
        loadMessages();

        binding.toolbarBackBtn.setOnClickListener(v->finish());
        binding.attachFab.setOnClickListener(v->imagePickDialog());
        binding.sendBtn.setOnClickListener(v->validateData());
    }

    private void loadMyInfo(){
        DatabaseReference ref=FirebaseDatabase.getInstance().getReference("Users");
        ref.child(firebaseAuth.getUid()).addValueEventListener(new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot snapshot){
                myName=""+snapshot.child("name").getValue();
                Log.d(TAG,"onDataChange: myName:"+myName);
            }
            @Override public void onCancelled(@NonNull DatabaseError error){}
        });
    }

    private void loadReceiptDetails(){
        Log.d(TAG,"loadReceiptDetails:");
        DatabaseReference ref=FirebaseDatabase.getInstance().getReference("Users");
        ref.child(receiptUid).addValueEventListener(new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot snapshot){
                try{
                    String name=""+snapshot.child("name").getValue();
                    String profileImageUrl=""+snapshot.child("profileImageUrl").getValue();
                    receiptFcmToken=""+snapshot.child("fcmToken").getValue();
                    Log.d(TAG,"onDataChange: name:"+name);
                    Log.d(TAG,"onDataChange: profileImageUrl:"+profileImageUrl);
                }catch(Exception e){Log.e(TAG,"loadReceiptDetails:",e);}
            }
            @Override public void onCancelled(@NonNull DatabaseError error){}
        });
    }

    private void loadMessages(){
        DatabaseReference ref=FirebaseDatabase.getInstance().getReference("Chats");
        ref.child(chatPath).addValueEventListener(new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot snapshot){
                chatArrayList.clear();
                for(DataSnapshot ds:snapshot.getChildren()){
                    try{
                        ModelChat modelChat=ds.getValue(ModelChat.class);
                        chatArrayList.add(modelChat);
                    }catch(Exception e){Log.e(TAG,"loadMessages:",e);}
                }
                AdapterChat adapterChat=new AdapterChat(ChatActivity.this,chatArrayList);
                binding.chatRv.setAdapter(adapterChat);
            }
            @Override public void onCancelled(@NonNull DatabaseError error){}
        });
    }

    private void imagePickDialog(){
        PopupMenu popupMenu=new PopupMenu(this,binding.attachFab);
        popupMenu.getMenu().add(Menu.NONE,1,1,"Camera");
        popupMenu.getMenu().add(Menu.NONE,2,2,"Gallery");
        popupMenu.show();
        popupMenu.setOnMenuItemClickListener(item->{
            int id=item.getItemId();
            if(id==1){
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
                    requestCameraPermissions.launch(new String[]{Manifest.permission.CAMERA});
                }else{
                    requestCameraPermissions.launch(new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE});
                }
            }else if(id==2){
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
                    pickImageGallery();
                }else{
                    requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }
            return true;
        });
    }

    private void pickImageCamera(){
        Log.d(TAG,"pickImageCamera:");
        ContentValues cv=new ContentValues();
        cv.put(MediaStore.Images.Media.TITLE,"CHAT_IMAGE_TEMP");
        cv.put(MediaStore.Images.Media.DESCRIPTION,"CHAT_IMAGE_TEMP_DESCRIPTION");
        imageUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
        Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,imageUri);
        cameraActivityResultLauncher.launch(intent);
    }

    private void pickImageGallery(){
        Log.d(TAG,"pickImageGallery:");
        Intent intent=new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryActivityResultLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> cameraActivityResultLauncher=registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result->{
            if(result.getResultCode()==Activity.RESULT_OK){
                Log.d(TAG,"onActivityResult: imageUri:"+imageUri);
                uploadToFirebaseStorage();
            }else Utils.toast(ChatActivity.this,"Cancelled!");
        });

    private final ActivityResultLauncher<Intent> galleryActivityResultLauncher=registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result->{
            if(result.getResultCode()==Activity.RESULT_OK){
                Intent data=result.getData();
                imageUri=data.getData();
                Log.d(TAG,"onActivityResult: imageUri:"+imageUri);
                uploadToFirebaseStorage();
            }else Utils.toast(ChatActivity.this,"Cancelled!");
        });

    private final ActivityResultLauncher<String[]> requestCameraPermissions=registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(),
        result->{
            Boolean granted=result.getOrDefault(Manifest.permission.CAMERA,false);
            if(granted)pickImageCamera();
            else Utils.toast(ChatActivity.this,"Permission denied!");
        });

    private final ActivityResultLauncher<String> requestStoragePermission=registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        isGranted->{
            if(isGranted)pickImageGallery();
            else Utils.toast(ChatActivity.this,"Permission denied!");
        });

    private void uploadToFirebaseStorage(){
        Log.d(TAG,"uploadToFirebaseStorage:");
        progressDialog.setMessage("Uploading image...");
        progressDialog.show();
        long timestamp=Utils.getTimestamp();
        String filePathAndName="ChatImages/"+timestamp;
        StorageReference storageRef=FirebaseStorage.getInstance().getReference(filePathAndName);
        storageRef.putFile(imageUri)
            .addOnProgressListener(snapshot->{
                double progress=100.0*snapshot.getBytesTransferred()/snapshot.getTotalByteCount();
                progressDialog.setMessage("Uploading: "+(int)progress+"%");
            })
            .addOnSuccessListener(taskSnapshot->storageRef.getDownloadUrl().addOnSuccessListener(uri->{
                sendMessage(Utils.MESSAGE_TYPE_IMAGE,""+uri,timestamp);
                progressDialog.dismiss();
            }))
            .addOnFailureListener(e->{
                progressDialog.dismiss();
                Utils.toast(ChatActivity.this,"Upload failed: "+e.getMessage());
            });
    }

    private void validateData(){
        String message=binding.messageEt.getText().toString().trim();
        if(message.isEmpty()){
            Utils.toast(this,"Enter message!");
            return;
        }
        long timestamp=Utils.getTimestamp();
        sendMessage(Utils.MESSAGE_TYPE_TEXT,message,timestamp);
    }

    private void sendMessage(String messageType,String message,long timestamp){
        Log.d(TAG,"sendMessage:"+message);
        progressDialog.setMessage("Sending message...");
        progressDialog.show();
        DatabaseReference refChat=FirebaseDatabase.getInstance().getReference("Chats");
        String keyId=refChat.push().getKey();
        HashMap<String,Object> map=new HashMap<>();
        map.put("messageId",keyId);
        map.put("messageType",messageType);
        map.put("message",message);
        map.put("fromUid",myUid);
        map.put("toUid",receiptUid);
        map.put("timestamp",timestamp);
        refChat.child(chatPath).child(keyId).setValue(map)
            .addOnSuccessListener(unused->{
                progressDialog.dismiss();
                prepareNotification(message);
            })
            .addOnFailureListener(e->{
                progressDialog.dismiss();
                Utils.toast(this,"Failed: "+e.getMessage());
            });
    }

    private void prepareNotification(String message){
        Log.d(TAG,"prepareNotification:");
        try{
            JSONObject notificationJo=new JSONObject();
            JSONObject dataJo=new JSONObject();
            JSONObject notifJo=new JSONObject();
            dataJo.put("notificationType",""+Utils.NOTIFICATION_TYPE_NEW_MESSAGE);
            dataJo.put("senderUid",""+firebaseAuth.getUid());
            notifJo.put("title",""+myName);
            notifJo.put("body",""+message);
            notifJo.put("sound","default");
            notificationJo.put("to",""+receiptFcmToken);
            notificationJo.put("notification",notifJo);
            notificationJo.put("data",dataJo);
            sendFcmNotification(notificationJo);
        }catch(Exception e){Log.e(TAG,"prepareNotification:",e);}
    }

    private void sendFcmNotification(JSONObject notificationJo){
        JsonObjectRequest req=new JsonObjectRequest(Request.Method.POST,"https://fcm.googleapis.com/fcm/send",notificationJo,
            response->Log.d(TAG,"Notification sent!"),
            error->Log.e(TAG,"sendFcmNotification:",error)){
            @Override public java.util.Map<String,String> getHeaders(){
                java.util.Map<String,String> headers=new HashMap<>();
                headers.put("Content-Type","application/json");
                headers.put("Authorization","key="+Utils.FCM_SERVER_KEY);
                return headers;
            }
        };
        VolleySingleton.getInstance(getApplicationContext()).addToRequestQueue(req);
    }
}
