package com.diaochan.rtmpbilibili;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private ScreenLive mScreenLive;

    //B站中 你的rtmp地址 + 直播码
    private static final String RTMP_URL = "rtmp://live-push.bilivideo.com/live-bvc/?streamname=live_345162489_81809986&key=03693092c85bd15a1d3fbbc227da0ad1&schedule=rtmp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && requestCode == RESULT_OK) {
            //产生录屏数据
            mediaProjection = mediaProjectionManager.getMediaProjection(requestCode, data);
            mScreenLive = new ScreenLive();
            mScreenLive.startLive(RTMP_URL, mediaProjection);
        }
    }

    public void startLive(View view) {
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, 100);
    }

    public void stopLive(View view) {

    }


    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);

        }
        return false;
    }
}