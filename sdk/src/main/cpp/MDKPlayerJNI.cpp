/*
 * Copyright (c) 2018-2023 WangBin <wbsecg1 at gmail.com>
 */
#include "jmi/jmi.h"
#include <jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <mdk/Player.h>
#include <mdk/MediaInfo.h>
#include <list>
#include <string>
#include <iostream>
#define  DECODE_TO_SURFACEVIEW 0

enum { // custom enum
    MEDIA_ERROR = -1,
    MEDIA_INFO,
    MEDIA_PREPARED,
    MEDIA_PLAYBACK_COMPLETE,
    MEDIA_BUFFERING_UPDATE,
    MEDIA_SEEK_COMPLETE,
    MEDIA_BIT_RATE_CHANGED,
};

enum {
    MEDIA_INFO_UNKNOWN				= 1,
    MEDIA_INFO_VIDEO_RENDERING_START= 3,
    MEDIA_INFO_BUFFERING_START		= 701,
    MEDIA_INFO_BUFFERING_END		= 702,
};

enum {
    MEDIA_ERROR_TIMED_OUT							= -110,
};

static void PostEvent(std::weak_ptr<jobject> wp, int what, int arg1 = 0, int arg2 = 0, jobject msg = nullptr)
{
    auto sp = wp.lock();
    if (!sp)
        return;
    auto obj = *sp;
    JNIEnv* env = jmi::getEnv();
    static jclass sClass = nullptr;
    static jmethodID sMethod = nullptr;
    if (!sMethod) {
        jclass clazz = env->GetObjectClass(obj);
        sClass = (jclass)env->NewGlobalRef(clazz);
        sMethod = env->GetStaticMethodID(sClass, "postEventFromNative", "(Ljava/lang/Object;IIILjava/lang/Object;)V");
    }
    env->CallStaticVoidMethod(sClass, sMethod, obj, what, arg1, arg2, msg);
}

using namespace MDK_NS;
extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    freopen("/sdcard/log.txt", "wta", stdout);
    freopen("/sdcard/loge.txt", "w", stderr);
    setLogHandler([](LogLevel v, const char* msg){
        if (v < LogLevel::Info)
            __android_log_print(ANDROID_LOG_WARN, "MDK-JNI", "%s", msg);
        else
            __android_log_print(ANDROID_LOG_DEBUG, "MDK-JNI", "%s", msg);
    });

    SetGlobalOption("profiler.gpu", 1);
    SetGlobalOption("logLevel", "all");

    std::clog << "JNI_OnLoad" << std::endl;
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK || !env) {
        std::clog << "GetEnv for JNI_VERSION_1_4 failed" << std::endl;
        return -1;
    }

    jmi::javaVM(vm);
    //SetGlobalOption("JavaVM", vm);
    return JNI_VERSION_1_4;
}

void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    std::cout << "JNI_OnUnload" << std::endl;
}

struct PlayerRef {
    ~PlayerRef() {
        delete player;
    }

    Player* player = new Player();
    std::shared_ptr<jobject> spobj;
    MediaStatus status = MediaStatus::NoMedia;
};

Player* get(jlong obj_ptr) {
    auto r = reinterpret_cast<PlayerRef*>(obj_ptr);
    return r->player;
}

#define MDK_JNI_FUNC(Name) Java_com_mediadevkit_sdk_##Name
#define MDK_JNI(Return, Name, ...) \
    JNIEXPORT Return JNICALL MDK_JNI_FUNC(Name) (JNIEnv *env, jobject thiz, jlong obj_ptr, ##__VA_ARGS__)

MDK_JNI(jlong, MDKPlayer_nativeCreate)
{
    auto pr = new PlayerRef();
    jobject objr = env->NewGlobalRef(thiz); // none weak is fine too
    pr->spobj = std::shared_ptr<jobject>(new jobject(objr), [](jobject* p){
        jmi::getEnv()->DeleteGlobalRef(*p);
        delete p;
    });
    auto p = pr->player;
    std::weak_ptr<jobject> w = pr->spobj;
    p->setTimeout(20000, [w](int64_t t){
        PostEvent(w, MEDIA_ERROR, MEDIA_ERROR_TIMED_OUT);
        return true;
    });
    p->onStateChanged([=](PlaybackState s){
        if (s == State::Stopped) {
            if (test_flag(p->mediaStatus() & MediaStatus::End)) // FIXME: invalid ptr p? called after nativeDestroy
                PostEvent(w, MEDIA_PLAYBACK_COMPLETE);
        }
    });
    p->onMediaStatusChanged([=](MediaStatus s){
        if (flags_added(pr->status, s, MediaStatus::Buffering))
            PostEvent(w, MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
        if (flags_removed(pr->status, s, MediaStatus::Buffering))
            PostEvent(w, MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
        if (flags_added(pr->status, s, MediaStatus::Prepared))
            PostEvent(w, MEDIA_PREPARED);
        pr->status = s;
        return true;
    });
    p->onEvent([w](const MediaEvent& e){
        __android_log_print(ANDROID_LOG_DEBUG, "MDK-JNI", "MediaEvent %d: %s %s", e.error, e.category.data(), e.detail.data());
        if (e.category == "reader.buffering") { // TODO: hash map
            PostEvent(w, MEDIA_BUFFERING_UPDATE, e.error);
            return false;
        }
        return false;
    });
    p->setAudioBackends({"AudioTrack", "OpenSL"});
    //p->setDecoders(MediaType::Audio, {"AMediaCodec:java=0", "FFmpeg"}); // AMediaCodec: higher cpu? FIXME: wrong result on x86
   // name: c2.android.hevc.decoder,c2.qti.hevc.decoder.low_latency,c2.qti.hevc.decoder.secure, OMX.google.hevc.decoder, OMX.qcom.video.decoder.hevc.low_latency, c2.dolby.avc-hevc.decoder, OMX.google.hevc.decoder
    //p->setDecoders(MediaType::Video, {"MediaCodec:ndk_codec=1", "FFmpeg"});
    p->setDecoders(MediaType::Video, {"AMediaCodec:java=0:copy=0:surface=1:image=1:async=0:low_latency=1", "FFmpeg"});

    p->setLoop(-1);
    return jlong(pr);
}

MDK_JNI(void, MDKPlayer_nativeDestroy)
{
    delete (PlayerRef*)obj_ptr;
}

MDK_JNI(void, MDKPlayer_nativeSetMedia, jstring url)
{
    const char* s = env->GetStringUTFChars(url, 0);
    get(obj_ptr)->setMedia(s);
    env->ReleaseStringUTFChars(url, s);
}

MDK_JNI(void, MDKPlayer_nativeSetNextMedia, jstring url)
{
    const char* s = env->GetStringUTFChars(url, 0);
    get(obj_ptr)->setNextMedia(s);
    env->ReleaseStringUTFChars(url, s);
}

MDK_JNI(void, MDKPlayer_nativeSetPlayList, jobjectArray urls)
{
    auto p = get(obj_ptr);
    auto url_list = std::make_shared<std::list<std::string>>();
    jsize len = env->GetArrayLength(urls);
    for (jsize i = 0; i < len; ++i) {
        auto si = (jstring)env->GetObjectArrayElement(urls, i);
        const char* s = env->GetStringUTFChars(si, 0);
        url_list->push_back(s);
        env->ReleaseStringUTFChars(si, s);
    }
    env->DeleteLocalRef(urls);
    const std::string url0 = url_list->front();
    __android_log_print(ANDROID_LOG_INFO, "MDK.JNI", "***************Play list 1st media: %s, count: %d", url_list->front().data(), (int)url_list->size());
    url_list->pop_front();
    p->currentMediaChanged([=]{
        if (url_list->empty()) {
            __android_log_print(ANDROID_LOG_INFO, "MDK.JNI", "***************Play list finished");
            return;
        }
        __android_log_print(ANDROID_LOG_INFO, "MDK.JNI", "*************currentMediaChanged now: %s, next: %s", p->url(), url_list->front().data());
        p->setNextMedia(url_list->front().data());
        url_list->pop_front();
    });
    p->setMedia(url0.data()); // set after currentMediaChanged(), otherwise 1st setNextMedia won't be called
}

MDK_JNI(void, MDKPlayer_nativeSetState, int state)
{
    get(obj_ptr)->set((State)state);
}

MDK_JNI(jint, MDKPlayer_nativeState)
{
    return jint(get(obj_ptr)->state());
}

MDK_JNI(void, MDKPlayer_nativeResizeVideoSurface, int width, int height)
{
#if !(DECODE_TO_SURFACEVIEW + 0)
    get(obj_ptr)->setVideoSurfaceSize(width, height);
#endif
}

MDK_JNI(void, MDKPlayer_nativeRenderVideo)
{
#if !(DECODE_TO_SURFACEVIEW + 0)
    get(obj_ptr)->renderVideo();
#endif
}

MDK_JNI(jlong, MDKPlayer_nativeSetSurface, jobject s, jlong win, int w, int h)
{
    std::cout << "~~~~~~~~~~~nativeSetSurface: " << s <<  std::endl;
    if (!obj_ptr)
        return 0; // called in surfaceDestroyed when player was already destroyed in onPause
    auto p = get(obj_ptr);
#if (DECODE_TO_SURFACEVIEW + 0)
    if (s) {
        //ANativeWindow* anw = s ? ANativeWindow_fromSurface(env, s) : nullptr; // TODO: release
        //p->setProperty("video.decoder", "window=" + std::to_string((intptr_t)anw));
        auto ss = (jobject)env->NewGlobalRef(s); // TODO: release
        p->setProperty("video.decoder", "surface=" + std::to_string((intptr_t)ss));
    }
#else
    p->updateNativeSurface(s, w, h);
#endif
    return (jlong)s;
}

MDK_JNI(jint, MDKPlayer_nativeGetDuration)
{
    if (!obj_ptr)
        return 0;
    auto p = get(obj_ptr);
    return (jint)p->mediaInfo().duration;
}

MDK_JNI(jint, MDKPlayer_nativePosition)
{
    if (!obj_ptr)
        return 0;
    auto p = get(obj_ptr);
    return (jint)p->position();
}

MDK_JNI(void, MDKPlayer_nativeSeek, jint ms)
{
    if (!obj_ptr)
        return;
    auto p = get(obj_ptr);
    p->seek(ms);
}
}
