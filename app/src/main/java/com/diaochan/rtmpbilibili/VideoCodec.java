package com.diaochan.rtmpbilibili;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Create by shijie.wang on 2021/3/11.
 */
public class VideoCodec extends Thread {

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ScreenLive screenLive;
    private MediaCodec mediaCodec;
    private boolean isLiving;

    private long startTimeMs; // 推流开始时间
    private long timeStampMs; // 时间戳

    public VideoCodec(ScreenLive screenLive) {
        this.screenLive = screenLive;
    }

    // 开启编码层并初始化
    public void startLive(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                720, 1280);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 400_000); // 所谓动态码率就是每次重新初始化MediaCodec
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 15); //直播中帧率一般15
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            // 编码数据到Surface
            Surface surface = mediaCodec.createInputSurface();
            virtualDisplay = mediaProjection.createVirtualDisplay("screen-codec",
                    720, 1280, 1,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        LiveTaskManager.getInstance().execute(this);
    }

    @Override
    public void run() {
        // 3. 编码开始
        isLiving = true;
        mediaCodec.start();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (isLiving) {
            
            // 直播要求I帧比较严格
            if (System.currentTimeMillis() - timeStampMs >= 2000) {
                // 3.1 手动去触发「每隔2s」通过DSP编码一个I帧
                Bundle param = new Bundle();
                param.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                mediaCodec.setParameters(param);
                timeStampMs = System.currentTimeMillis();
            }
            
            // 获取编码后数据
            int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
            while (outputIndex >= 0 && isLiving) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                byte[] data = new byte[bufferInfo.size];
                outputBuffer.get(data);
                LogUtil.writeContent(data);

                // 4.发送编码包「封装成RtmpPackage」给传输层
                if (startTimeMs == 0) {
                    startTimeMs = bufferInfo.presentationTimeUs / 1000;
                }
                RtmpPackage rtmpPackage = new RtmpPackage(RtmpPackage.RTMP_PACKET_TYPE_VIDEO, 
                        data, bufferInfo.presentationTimeUs / 1000 - startTimeMs);
                screenLive.addPackage(rtmpPackage);
                mediaCodec.releaseOutputBuffer(outputIndex, false);
                outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        }
        
        // 5. 推流完成释放
        isLiving = false;
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        virtualDisplay.release();
        virtualDisplay = null;
        startTimeMs = 0;
    }
} 