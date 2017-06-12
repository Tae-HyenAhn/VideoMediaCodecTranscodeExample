package com.taehyen.videomediacodectranscodeexample;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.util.ArrayList;
import java.util.StringTokenizer;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import wseemann.media.FFmpegMediaMetadataRetriever;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final String TARGET_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/SpringTest/input.mp4";

    @BindView(R.id.start_btn) Button startButton;
    private ProgressDialog dialog;

    private SoftInputSurfaceThread transcodeThread;
    private SoftInputSurfaceThread.OnSoftInputSurfaceListener onSoftInputSurfaceListener = new SoftInputSurfaceThread.OnSoftInputSurfaceListener() {
        @Override
        public void onFinishTransCoding() {
            Toast.makeText(getApplicationContext(), "Transcoding Finished!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }
    };

    private PermissionListener permissionListener = new PermissionListener() {
        @Override
        public void onPermissionGranted() {
            Toast.makeText(getApplicationContext(), "Permission OK!", Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onPermissionDenied(ArrayList<String> deniedPermissions) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        getPermissions();


    }

    private void getPermissions(){
        new TedPermission(this)
                .setPermissionListener(permissionListener)
                .setDeniedMessage("Denied")
                .setPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .check();
    }

    @OnClick(R.id.start_btn) void startButtonClick(){
        dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage("Transcoding Videos..");
        dialog.show();

        transcodeThread = new SoftInputSurfaceThread();
        transcodeThread.init(this, TARGET_PATH, onSoftInputSurfaceListener);
        transcodeThread.run();

    }




}
