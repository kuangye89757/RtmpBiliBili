package com.diaochan.rtmpbilibili;

/**
 * Create by shijie.wang on 2021/3/11.
 */
public class RtmpPackage {
    
    private byte[] buffer; // 帧数据
    private long tms; // 时间戳（ms）

    public RtmpPackage(byte[] buffer, long tms) {
        this.buffer = buffer;
        this.tms = tms;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public RtmpPackage setBuffer(byte[] buffer) {
        this.buffer = buffer;
        return this;
    }

    public long getTms() {
        return tms;
    }

    public RtmpPackage setTms(long tms) {
        this.tms = tms;
        return this;
    }
} 