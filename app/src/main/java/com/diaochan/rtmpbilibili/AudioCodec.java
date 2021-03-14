package com.diaochan.rtmpbilibili;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Create by shijie.wang on 2021/3/14.
 */
public class AudioCodec extends Thread {

    private static final String TAG = "AudioCodec";
    private ScreenLive screenLive;
    private AudioRecord audioRecord;
    private MediaCodec mediaCodec;
    private int minBufferSize;
    private boolean isRecording;
    private long startTimeMs;
    private static final int SAMPLE_RATE_IN_HZ = 44100;//采样率
    private static final int CHANNEL_COUNT = 1;//通道数

    public AudioCodec(ScreenLive screenLive){
        this.screenLive = screenLive;
    }

    // 开启编码层并初始化
    public void startLive() {
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                SAMPLE_RATE_IN_HZ, CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);// 录音质量
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64_000);// 码率

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            //最小audio的缓冲区大小 【已知量化位数、采样率、通道数 缓冲区大小则固定】
            minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            //麦克风
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_IN_HZ,
                    AudioFormat.CHANNEL_IN_MONO /** 单通道 */,
                    AudioFormat.ENCODING_PCM_16BIT /** 16位采样点 */, minBufferSize);
        } catch (Exception e) { }
        LiveTaskManager.getInstance().execute(this);
    }

    @Override
    public void run() {
        // 根据RTMP协议音频，需要先发送一个固定数据包用于告知服务端，准备开始接受音频
        byte[] audioDecodeSpecificInfo = {0x12, 0X08};
        RtmpPackage rtmpPackage = new RtmpPackage(RtmpPackage.RTMP_PACKET_TYPE_AUDIO_HEADER,
                audioDecodeSpecificInfo, 0);
        screenLive.addPackage(rtmpPackage);

        isRecording = true;
        mediaCodec.start();
        audioRecord.startRecording();// 音频编码
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        byte[] buffer = new byte[minBufferSize];
        while (isRecording) {
            // 3. 获取编码后数据，从麦克风里读取出来「PCM数据」到buffer,返回读取出来的长度
            int len = audioRecord.read(buffer, 0, buffer.length);
            if (len <= 0) {
                continue;
            }
            int inputIndex = mediaCodec.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputIndex);
                inputBuffer.clear();
                inputBuffer.put(buffer, 0, len); //编码buffer中len长度，而非buffer.length
                mediaCodec.queueInputBuffer(inputIndex, 0, len,
                        System.nanoTime() / 1000, 0);
            }
            int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000);
            while (outputIndex >= 0 && isRecording) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                byte[] data = new byte[bufferInfo.size];
                outputBuffer.get(data);
                LogUtil.writeContent(data);
                if (startTimeMs == 0) { // 4.发送编码包「封装成RtmpPackage」给传输层
                    startTimeMs = bufferInfo.presentationTimeUs / 1000;
                }
                rtmpPackage = new RtmpPackage(RtmpPackage.RTMP_PACKET_TYPE_AUDIO_DATA,
                        data, bufferInfo.presentationTimeUs / 1000 - startTimeMs);
                screenLive.addPackage(rtmpPackage);
                mediaCodec.releaseOutputBuffer(outputIndex, false);
                outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        }

        // 5. 推流完成释放
        isRecording = false;
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        startTimeMs = 0;
    }
} 