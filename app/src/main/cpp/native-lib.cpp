#include <jni.h>
#include <string>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,"David",__VA_ARGS__)

extern "C" {
#include "librtmp/rtmp.h"
}

/**
 * 通过指针访问数组，需要知道长度
 * int8_t 一个字节 正好定义指针
 */
typedef struct {
    RTMP *rtmp;
    int8_t *sps; //数组地址用8位表示
    int8_t *pps;
    int16_t sps_len; //长度为2个字节 16位表示
    int16_t pps_len;
} Live;

// 全局
Live *live = NULL;

int sendVideo(int8_t *data, const int len, const long tms);

void prepareVideo(int8_t *buf, const int len, const long tms;

RTMPPacket *createVideoSPSPPSPackage(Live *live);

int sendAudio(int8_t *data, const int type, const int len, const long tms);

RTMPPacket *createAudioPackage(int8_t *data,const int type, const int len, const long tms, Live *live);

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diaochan_rtmpbilibili_ScreenLive_connect(JNIEnv *env, jobject thiz, jstring _url) {

    const char *url = env->GetStringUTFChars(_url, 0);
    int ret;
    do {
        // 实例化
        live = static_cast<Live *>(malloc(sizeof(Live)));
        memset(live, 0, sizeof(live));

        LOGI("第一步：分配RTMP内存空间，返回RTMP指针");
        live->rtmp = RTMP_Alloc();

        LOGI("第二步：初始化RTMP");
        RTMP_Init(live->rtmp);
        live->rtmp->Link.timeout = 10; // 设置10s超时

        LOGI("第三步：设置地址 %s", url);
        ret = RTMP_SetupURL(live->rtmp, (char *) url);
        if (!ret) {
            LOGI("设置地址 error %s", url);
            break;
        }

        LOGI("第四步：切换为输出模式");
        RTMP_EnableWrite(live->rtmp);

        LOGI("第五步：连接服务器");
        ret = RTMP_Connect(live->rtmp, 0);
        if (!ret) {
            LOGI("连接服务器 error");
            break;
        }

        LOGI("第六步：连接流");
        ret = RTMP_ConnectStream(live->rtmp, 0);
        if (!ret) {
            LOGI("连接流 error");
            break;
        }
        LOGI("connect success");

    } while (0);

    if (!ret && live) {
        LOGI("失败释放资源");
        free(live);
        live = nullptr;
    }
    env->ReleaseStringUTFChars(_url, url);
    return ret;

}


/**
 * 以 00 00 00 01 68 作为分隔 找到 SPS 和 PPS
 * 00000001 67 64 00 28acb402201e3cbca41408081b4284d4 「sps」
 * 00000001 68 ee 06 e2 c0 「pps」
 */
void prepareVideo(int8_t *buf, jint len, jlong tms) {
    for (int i = 0; i < len; i++) {
        // 设置边界：超出分隔符  ee 06 e2 c0 之后的数据不考虑
        if (i + 4 < len) {
            if (buf[i] == 0x00 && buf[i + 1] == 0x00 && buf[i + 2] == 0x00 && buf[i + 3] == 0x01) {
                if (buf[i + 4] == 0x68) {
                    // sps
                    live->sps_len = i - 4; // 不包含00000001
                    live->sps = static_cast<int8_t *>(malloc(sizeof(live->sps_len)));
                    memcpy(live->sps, buf + 4, live->sps_len);

                    // pps
                    live->pps_len = len - (4 + live->sps_len) - 4; // 帧长 - sps - 00000001分隔符
                    live->pps = static_cast<int8_t *>(malloc(sizeof(live->pps_len)));
                    memcpy(live->pps, buf + 4 + live->sps_len + 4, live->pps_len);
                    LOGI("sps: %d, pps: %d", live->sps_len, live->pps_len);
                    break;
                }
            }
        }
    }
}

RTMPPacket *createVideoSPSPPSPackage(Live *live) {
    //sps和pps
    int body_size = 16 + live->sps_len + live->pps_len;
    RTMPPacket *rtmpPacket = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    RTMPPacket_Alloc(rtmpPacket, body_size);

    rtmpPacket->m_packetType = RTMP_PACKET_TYPE_VIDEO; //视频数据包
    rtmpPacket->m_nBodySize = body_size; //数据包大小
    rtmpPacket->m_nChannel = 0x04; //通道id 随意 但不同通道不能重复 这里定视频通道ID为0x04
    rtmpPacket->m_nTimeStamp = 0;
    rtmpPacket->m_hasAbsTimestamp = 0; // 不使用绝对时间，使用相对时间
    rtmpPacket->m_headerType = RTMP_PACKET_SIZE_LARGE; //header的数据较大
    rtmpPacket->m_nInfoField2 = live->rtmp->m_stream_id; // id

    //类型
    int i = 0;
    rtmpPacket->m_body[i++] = 0x17;
    rtmpPacket->m_body[i++] = 0x00;
    rtmpPacket->m_body[i++] = 0x00;
    rtmpPacket->m_body[i++] = 0x00;
    rtmpPacket->m_body[i++] = 0x00;

    //版本
    rtmpPacket->m_body[i++] = 0x01;

    //编码规格 sps[1] + sps[1] + sps[1]
    rtmpPacket->m_body[i++] = live->sps[1];
    rtmpPacket->m_body[i++] = live->sps[2];
    rtmpPacket->m_body[i++] = live->sps[3];

    //NALU长度
    rtmpPacket->m_body[i++] = 0xFF;

    //SPS个数
    rtmpPacket->m_body[i++] = 0xE1;

    //SPS长度
    rtmpPacket->m_body[i++] = (live->sps_len >> 8) & 0xFF; //高8位
    rtmpPacket->m_body[i++] = live->sps_len & 0xFF; //低8位

    //SPS内容
    memcpy(&rtmpPacket->m_body[i], live->sps, live->sps_len);
    i += live->sps_len;

    //PPS个数
    rtmpPacket->m_body[i++] = 0x01;

    //PPS长度
    rtmpPacket->m_body[i++] = (live->pps_len >> 8) & 0xFF; //高8位
    rtmpPacket->m_body[i++] = live->pps_len & 0xFF; //低8位

    //PPS内容
    memcpy(&rtmpPacket->m_body[i], live->pps, live->pps_len);
    return rtmpPacket;
}

RTMPPacket *createVideoPackage(int8_t *buf, jint len, const long tms, Live *live) {

    len -= 4;
    int body_size = 9 + len;
    RTMPPacket *rtmpPacket = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    RTMPPacket_Alloc(rtmpPacket, body_size);

    rtmpPacket->m_packetType = RTMP_PACKET_TYPE_VIDEO; //视频数据包
    rtmpPacket->m_nBodySize = body_size; //数据包大小
    rtmpPacket->m_nChannel = 0x04; //通道id 随意 但不同通道不能重复 这里定视频通道ID为0x04
    rtmpPacket->m_nTimeStamp = tms; //时间戳
    rtmpPacket->m_hasAbsTimestamp = 0; // 不使用绝对时间，使用相对时间
    rtmpPacket->m_headerType = RTMP_PACKET_SIZE_LARGE; //header的数据较大
    rtmpPacket->m_nInfoField2 = live->rtmp->m_stream_id; // id

    buf += 4; //首地址跳过分隔符
    if(buf[0] == 0x65){
        //关键帧
        rtmpPacket->m_body[0] = 0x17;
        LOGI("发送关键帧");
    }else{
        // 非关键帧
        rtmpPacket->m_body[0] = 0x27;
        LOGI("发送非关键帧");
    }
    rtmpPacket->m_body[1] = 0x01;
    rtmpPacket->m_body[2] = 0x00;
    rtmpPacket->m_body[3] = 0x00;
    rtmpPacket->m_body[4] = 0x00;
    
    //数据长度 4个字节
    rtmpPacket->m_body[5] = (len >> 24) & 0xFF;
    rtmpPacket->m_body[6] = (len >> 16) & 0xFF;
    rtmpPacket->m_body[7] = (len >> 8) & 0xFF;
    rtmpPacket->m_body[8] = len & 0xFF;

    //数据
    memcpy(&rtmpPacket->m_body[9], buf, len); 
    return rtmpPacket;
}

int sendPacket(RTMPPacket *packet) {
    int ret = RTMP_SendPacket(live->rtmp, packet, 1);
    RTMPPacket_Free(packet);
    free(packet);
    return ret;
}

/**
 * 传递第一帧[见codec.h264] ： 00000001 67 64 00 28acb402201e3cbca41408081b4284d4 「sps」
 *                          00000001 68 ee 06 e2 c0 「pps」
 */
int sendVideo(int8_t *buf, jint len, jlong tms) {
    int ret = 0;
    if (buf[4] == 0x67) {
        //缓存SPS和PPS到全局，无需推流
        if (!live && (!live->pps || !live->sps)) {
            prepareVideo(buf, len, tms);
        }
        return ret;
    }

    if (buf[4] == 0x65) {
        //先推SPS和PPS
        RTMPPacket *SPSPacket = createVideoSPSPPSPackage(live);
        sendPacket(SPSPacket);
    }

    //推送I\P\B帧
    RTMPPacket *packet = createVideoPackage(buf, len, tms, live);
    ret = sendPacket(packet);
    return ret;
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diaochan_rtmpbilibili_ScreenLive_sendData(JNIEnv *env, jobject thiz, jbyteArray _data,
                                                   jint type, jint len, jlong tms) {
    int ret;
    jbyte *data = env->GetByteArrayElements(_data, 0);
    switch (type) {
        case 0:
            // 推送视频
            ret = sendVideo(data, len, tms);
            LOGI("send video packet length : %d\n", len);
            break;
            
        default:
            // 推送音频
            ret = sendAudio(data, type, len, tms);
            LOGI("send audio packet length : %d\n", len);
            break;
    }
    env->ReleaseByteArrayElements(_data, data, 0);
    return ret;
}

int sendAudio(int8_t *data, const int type, const int len, const long tms) {
    RTMPPacket *packet = createAudioPackage(data, type, len, tms, live);
    int ret = sendPacket(packet);
    return ret;
}

RTMPPacket *createAudioPackage(int8_t *data, const int type, const int len, const long tms, Live *live) {
    // 组装音频数据包  2个固定字节(0XAF 0X00/0X01) + 数据长度
    RTMPPacket *rtmpPacket = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    int body_size = len + 2;
    RTMPPacket_Alloc(rtmpPacket , body_size);

    rtmpPacket->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    rtmpPacket->m_nChannel = 0X05; //通道ID，要不同于视频的
    rtmpPacket->m_nBodySize = body_size; //数据包大小
    rtmpPacket->m_nTimeStamp = tms; //时间戳
    rtmpPacket->m_hasAbsTimestamp = 0; // 不使用绝对时间，使用相对时间
    rtmpPacket->m_headerType = RTMP_PACKET_SIZE_LARGE; //header的数据较大
    rtmpPacket->m_nInfoField2 = live->rtmp->m_stream_id; // id

    rtmpPacket->m_body[0] = 0xAF;
    if(type == 2){
        // 第一个音频数据包
        rtmpPacket->m_body[1] = 0x00;
    }else{
        rtmpPacket->m_body[1] = 0x01;
    }
    
    memcpy(&rtmpPacket->m_body[2], data, len);
    return rtmpPacket;
}
