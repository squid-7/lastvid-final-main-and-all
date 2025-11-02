package com.example.chatapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.chatapp.databinding.ActivityMainBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;

/*
 MainActivity
 Features:
 - ViewBinding (ActivityMainBinding)
 - Bottom navigation switching between Home, Chats, MyAds, Profile fragments
 - Toolbar updates
 - Request notification permission on Android 13+ and update FCM token
 - Update online/offline status in Firebase Realtime DB
 - Helper methods for fragment transactions and toolbar button handling
 NOTE: Replace package name to match your app and ensure fragments (HomeFragment,ChatsFragment,MyAdsFragment,ProfileFragment) exist.
*/

public class MainActivity extends AppCompatActivity{
    private static final String TAG="MainActivity";
    private ActivityMainBinding binding;
    private FirebaseAuth firebaseAuth;
    private String myUid;
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        binding=ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth=FirebaseAuth.getInstance();
        myUid=firebaseAuth.getCurrentUser()!=null?firebaseAuth.getCurrentUser().getUid():null;

        initPermissionLauncher();
        setupToolbar();
        setupBottomNavigation();
        defaultFragment();

        // If user not logged in, send to login screen (you can customize)
        if(firebaseAuth.getCurrentUser()==null){
            startLoginOptions();
            return;
        }

        // Ask for notification permission (Android 13+) and update token
        checkAndRequestNotificationPermissionThenUpdateToken();

        // Example toolbar buttons wiring (search/profile/menu)
        binding.toolbarSearchBtn.setOnClickListener(v->onSearchClicked());
        binding.toolbarProfileBtn.setOnClickListener(v->showProfileFragment());
    }

    private void initPermissionLauncher(){
        requestNotificationPermissionLauncher=registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted->{
                if(isGranted){
                    Log.d(TAG,"Notification permission granted");
                    updateFCMToken();
                }else{
                    Log.d(TAG,"Notification permission denied");
                    updateFCMToken(); // still try to update token (token may exist)
                }
            });
    }

    private void checkAndRequestNotificationPermissionThenUpdateToken(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)== PackageManager.PERMISSION_GRANTED){
                updateFCMToken();
            }else{
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }else{
            updateFCMToken();
        }
    }

    private void updateFCMToken(){
        if(firebaseAuth.getCurrentUser()==null)return;
        String uid=firebaseAuth.getUid();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(new OnSuccessListener<String>(){
            @Override public void onSuccess(String token){
                try{
                    Log.d(TAG,"FCM token:"+token);
                    HashMap<String,Object> map=new HashMap<>();
                    map.put("fcmToken",token);
                    DatabaseReference ref=FirebaseDatabase.getInstance().getReference("Users");
                    ref.child(uid).updateChildren(map)
                        .addOnSuccessListener(aVoid->Log.d(TAG,"FCM token updated on DB"))
                        .addOnFailureListener(e->Log.e(TAG,"Failed update FCM token",e));
                }catch(Exception e){
                    Log.e(TAG,"updateFCMToken:onSuccess: ",e);
                }
            }
        }).addOnFailureListener(new OnFailureListener(){
            @Override public void onFailure(@NonNull Exception e){
                Log.e(TAG,"getToken failed",e);
            }
        });
    }

    private void setupToolbar(){
        // initial toolbar title
        binding.toolbarTitleTv.setText("Home");
        binding.toolbarBackBtn.setOnClickListener(v->onBackPressed());
    }

    private void setupBottomNavigation(){
        binding.bottomNav.setOnItemSelectedListener(item->{
            int id=item.getItemId();
            if(id==R.id.menu_home){
                showHomeFragment();
                return true;
            }else if(id==R.id.menu_chats){
                showChatsFragment();
                return true;
            }else if(id==R.id.menu_myads){
                showMyAdsFragment();
                return true;
            }else if(id==R.id.menu_profile){
                showProfileFragment();
                return true;
            }
            return false;
        });
    }

    private void defaultFragment(){
        // show Home fragment by default
        showHomeFragment();
    }

    private void showHomeFragment(){
        binding.toolbarTitleTv.setText("Home");
        HomeFragment fragment=new HomeFragment();
        replaceFragment(fragment,"HomeFragment");
    }

    private void showChatsFragment(){
        binding.toolbarTitleTv.setText("Chats");
        ChatsFragment fragment=new ChatsFragment();
        replaceFragment(fragment,"ChatsFragment");
    }

    private void showMyAdsFragment(){
        binding.toolbarTitleTv.setText("My Ads");
        MyAdsFragment fragment=new MyAdsFragment();
        replaceFragment(fragment,"MyAdsFragment");
    }

    private void showProfileFragment(){
        binding.toolbarTitleTv.setText("Profile");
        ProfileFragment fragment=new ProfileFragment();
        replaceFragment(fragment,"ProfileFragment");
    }

    private void replaceFragment(Fragment fragment,String tag){
        try{
            FragmentTransaction ft=getSupportFragmentManager().beginTransaction();
            ft.replace(binding.fragmentsFl.getId(),fragment,tag);
            ft.commit();
        }catch(Exception e){
            Log.e(TAG,"replaceFragment:",e);
        }
    }

    private void onSearchClicked(){
        // Example: start SearchActivity or show search UI
        Intent i=new Intent(this,SearchActivity.class);
        startActivity(i);
    }

    private void startLoginOptions(){
        // send user to login screen
        Intent i=new Intent(this,LoginOptionsActivity.class);
        startActivity(i);
        finish();
    }

    @Override
    protected void onResume(){
        super.onResume();
        setUserStatus("online");
    }

    @Override
    protected void onPause(){
        super.onPause();
        setUserStatus("offline");
    }

    private void setUserStatus(String status){
        try{
            if(firebaseAuth.getCurrentUser()==null)return;
            String uid=firebaseAuth.getUid();
            DatabaseReference ref=FirebaseDatabase.getInstance().getReference("Users").child(uid);
            HashMap<String,Object> map=new HashMap<>();
            map.put("status",status);
            if("online".equals(status)){
                map.put("lastSeen","online");
            }else{
                map.put("lastSeen",String.valueOf(System.currentTimeMillis()));
            }
            ref.updateChildren(map)
                .addOnSuccessListener(aVoid->Log.d(TAG,"Status updated:"+status))
                .addOnFailureListener(e->Log.e(TAG,"Status update failed",e));
        }catch(Exception e){
            Log.e(TAG,"setUserStatus:",e);
        }
    }

    // optional: expose method to logout
    private void logout(){
        try{
            if(firebaseAuth.getCurrentUser()!=null)firebaseAuth.signOut();
            startLoginOptions();
        }catch(Exception e){
            Log.e(TAG,"logout:",e);
        }
    }
}
