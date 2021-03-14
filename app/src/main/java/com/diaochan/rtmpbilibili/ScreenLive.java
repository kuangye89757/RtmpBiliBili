package com.diaochan.rtmpbilibili;

import android.media.projection.MediaProjection;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Create by shijie.wang on 2021/3/11.
 */
public class ScreenLive extends Thread {
    static {
        System.loadLibrary("native-lib");
    }
    
    private static final String TAG = "ScreenLive";
    private LinkedBlockingQueue<RtmpPackage> queue = new LinkedBlockingQueue<>();
    private boolean isLiving;
    private String url;
    private MediaProjection mediaProjection;
    private VideoCodec videoCodec;
    private AudioCodec audioCodec;

    public void addPackage(RtmpPackage rtmpPackage) {
        // 生产者
        if (!isLiving) {
            return;
        }
        queue.add(rtmpPackage);
    }

    // 1.开启推送
    public void startLive(String url, MediaProjection mediaProjection) {
        this.url = url;
        this.mediaProjection = mediaProjection;
        LiveTaskManager.getInstance().execute(this);
    }

    @Override
    public void run() {
        if (!connect(url)) {
            Log.d(TAG, "连接错误，推流失败~");
            return;
        }
        // 2. 初始化编码层
        videoCodec = new VideoCodec(this);
        audioCodec = new AudioCodec(this);
        videoCodec.startLive(mediaProjection);
        audioCodec.startLive();
        isLiving = true;

        while (isLiving) {
            RtmpPackage rtmpPackage = null;
            try {
                rtmpPackage = queue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            byte[] bytes = rtmpPackage.getBuffer();
            if (bytes != null && bytes.length != 0) {
                // 5. 获取编码后的数据, 交由native层推流
                sendData(bytes,rtmpPackage.getType(), bytes.length, rtmpPackage.getTms());
            }
        }
    }


    private native boolean connect(String url);

    private native boolean sendData(byte[] data, int type, int len, long tms);
} 