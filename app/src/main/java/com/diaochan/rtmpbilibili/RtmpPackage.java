package com.diaochan.rtmpbilibili;

/**
 * Create by shijie.wang on 2021/3/11.
 */
public class RtmpPackage {
    
    private byte[] buffer; // 帧数据
    private long tms; // 时间戳（ms）
    private int type;
    
    public static final int RTMP_PACKET_TYPE_VIDEO = 0;
    public static final int RTMP_PACKET_TYPE_AUDIO_DATA = 1;
    public static final int RTMP_PACKET_TYPE_AUDIO_HEADER = 2;

    public RtmpPackage(int type, byte[] buffer, long tms) {
        this.type = type;
        this.buffer = buffer;
        this.tms = tms;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public long getTms() {
        return tms;
    }

    public int getType() {
        return type;
    }
} 