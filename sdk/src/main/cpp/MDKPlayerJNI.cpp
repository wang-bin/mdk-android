/*
 * Copyright (c) 2018-2025 WangBin <wbsecg1 at gmail.com>
 */
#include "jmi/jmi.h"
#include <jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <vulkan/vulkan.h> // before any mdk header
#include <mdk/Player.h>
#include <mdk/MediaInfo.h>
#include <list>
#include <string>
#include <iostream>
#define  DECODE_TO_SURFACEVIEW 0
#define USE_VULKAN 0

#define LOG_TAG "mdk-kni"
#define log_info(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

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
    //freopen("/sdcard/log.txt", "wta", stdout); // java.lang.IllegalArgumentException: Primary directory null not allowed for content://media/external_primary/file; allowed directories are [Download, Documents] filePath = /storage/emulated/0/loge.txt callingPackageName = com.mediadevkit.mdkplayer
    //freopen("/sdcard/loge.txt", "w", stderr);
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
    if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK || !env) {
        std::clog << "GetEnv for JNI_VERSION_1_4 failed" << std::endl;
        return -1;
    }

    jmi::javaVM(vm);
    SetGlobalOption("JavaVM", vm);
    return JNI_VERSION_1_6;
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
    jobject surface = nullptr;
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
    p->onMediaStatus([=](MediaStatus oldVal, MediaStatus newVal){
        if (flags_added(oldVal, newVal, MediaStatus::Buffering))
            PostEvent(w, MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
        if (flags_removed(oldVal, newVal, MediaStatus::Buffering))
            PostEvent(w, MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
        if (flags_added(oldVal, newVal, MediaStatus::Prepared))
            PostEvent(w, MEDIA_PREPARED);
        return true;
    });
    p->onEvent([w](const MediaEvent& e){
        //__android_log_print(ANDROID_LOG_DEBUG, "MDK-JNI", "MediaEvent %d: %s %s", e.error, e.category.data(), e.detail.data());
        if (e.category == "reader.buffering") { // TODO: hash map
            PostEvent(w, MEDIA_BUFFERING_UPDATE, e.error);
            return false;
        }
        return false;
    });
    //p->setActiveTracks(MediaType::Audio, {});
    //p->setAudioBackends({ "OpenSL"});
    //p->setDecoders(MediaType::Audio, {"AMediaCodec:java=0", "FFmpeg"}); // AMediaCodec: higher cpu? FIXME: wrong result on x86
   // name: c2.android.hevc.decoder,c2.qti.hevc.decoder.low_latency,c2.qti.hevc.decoder.secure, OMX.google.hevc.decoder, OMX.qcom.video.decoder.hevc.low_latency, c2.dolby.avc-hevc.decoder, OMX.google.hevc.decoder
    //p->setDecoders(MediaType::Video, {"MediaCodec:ndk_codec=1", "FFmpeg"});
    p->setDecoders(MediaType::Video, {"AMediaCodec:dv=0:acquire=latest:ndk_codec=1:java=0:copy=0:surface=1:image=1:async=1:low_latency=1", "FFmpeg"});
    //putenv("EGL_HDR_METADATA=0");
    putenv("GL_YUV_SAMPLER=1");
    //putenv("LOG_SHADER=1");
    return jlong(pr);
}

MDK_JNI(void, MDKPlayer_nativeDestroy)
{
    delete (PlayerRef*)obj_ptr;
}

MDK_JNI(void, MDKPlayer_nativeSetMedia, jstring url)
{
    if (!url) {
        get(obj_ptr)->setMedia(nullptr);
        return;
    }
    const char* s = env->GetStringUTFChars(url, nullptr);
    get(obj_ptr)->setMedia(s);
    env->ReleaseStringUTFChars(url, s);
}

MDK_JNI(void, MDKPlayer_nativeSetNextMedia, jstring url)
{
    if (!url) {
        get(obj_ptr)->setNextMedia(nullptr);
        return;
    }
    const char* s = env->GetStringUTFChars(url, nullptr);
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
        const char* s = env->GetStringUTFChars(si, nullptr);
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
# if (USE_VULKAN + 0)
    p->setProperty("video.decoder", "surface=0"); // surface is not supported yet
    static jobject ss = nullptr;
    //if (ss)
    //    return (jlong)s;
    if (w <= 0 || h <= 0) // TODO: required by vk. BUS_ADRALN
        return (jlong)s;
    ss = s;
    VulkanRenderAPI vkra{};
    //vkra.debug = 1; // crash if no layer found
    std::clog << w << "x" << h << "device_index: " << vkra.device_index << std::endl;
    p->setRenderAPI(&vkra, s);
# endif
    p->updateNativeSurface(s, w, h);
#endif
    reinterpret_cast<PlayerRef*>(obj_ptr)->surface = s;
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

MDK_JNI(void, MDKPlayer_nativeSetColorSpace, jint value)
{
    auto r = reinterpret_cast<PlayerRef*>(obj_ptr);
    auto p = get(obj_ptr);
    p->set(ColorSpace(value)); // store default value globally, will be used if surface is changed
    p->set(ColorSpace(value), r->surface); // apply for current surface
}
}


// Kotlin bindings

struct JavaMediaInfo {
    jclass mediaInfoClass;
    jmethodID mediaInfoConstructor;

    jclass videoStreamClass;
    jclass videoCodecClass;
    jmethodID videoStreamConstructor;
    jmethodID videoCodecConstructor;

    jclass audioStreamClass;
    jclass audioCodecClass;
    jmethodID audioStreamConstructor;
    jmethodID audioCodecConstructor;

    jclass subtitleStreamClass;
    jclass subtitleCodecClass;
    jmethodID subtitleStreamConstructor;
    jmethodID subtitleCodecConstructor;

    jclass hashMapClass;
    jmethodID hashMapConstructor;
    jmethodID hashMapPut;

    jclass arrayList;
    jmethodID arrayListConstructor;
    jmethodID arrayListAdd;
};

static JavaMediaInfo javaMediaInfo(JNIEnv* env) {
  auto mediaInfo = env->FindClass("com/mediadevkit/sdk/MdkMediaInfo");
  auto videoStream = env->FindClass("com/mediadevkit/sdk/MdkVideoStream");
  auto videoCodec = env->FindClass("com/mediadevkit/sdk/MdkVideoCodec");
  auto audioStream = env->FindClass("com/mediadevkit/sdk/MdkAudioStream");
  auto audioCodec = env->FindClass("com/mediadevkit/sdk/MdkAudioCodec");
  auto subtitleStream = env->FindClass("com/mediadevkit/sdk/MdkSubtitle");
  auto subtitleCodec = env->FindClass("com/mediadevkit/sdk/MdkSubtitleCodec");
  auto hashMapClass = env->FindClass("java/util/HashMap");
  auto arrayListClass = env->FindClass("java/util/ArrayList");

  return JavaMediaInfo{
      .mediaInfoClass = (jclass) env->NewGlobalRef(mediaInfo),
      .mediaInfoConstructor = env->GetMethodID(mediaInfo, "<init>", "(JJJJLjava/lang/String;ILjava/util/List;Ljava/util/List;Ljava/util/List;)V"),
      .videoStreamClass = (jclass) env->NewGlobalRef(videoStream),
      .videoCodecClass = (jclass) env->NewGlobalRef(videoCodec),
      .videoStreamConstructor = env->GetMethodID(videoStream, "<init>", "(IJJJILjava/util/Map;Lcom/mediadevkit/sdk/MdkVideoCodec;)V"),
      .videoCodecConstructor = env->GetMethodID(videoCodec, "<init>", "(Ljava/lang/String;IJIIFILjava/lang/String;IIIFII)V"),
      .audioStreamClass = (jclass) env->NewGlobalRef(audioStream),
      .audioCodecClass = (jclass) env->NewGlobalRef(audioCodec),
      .audioStreamConstructor = env->GetMethodID(audioStream, "<init>", "(IJJJLjava/util/Map;Lcom/mediadevkit/sdk/MdkAudioCodec;)V"),
      .audioCodecConstructor = env->GetMethodID(audioCodec, "<init>", "(Ljava/lang/String;IJIIFZZZIIIII)V"),
      .subtitleStreamClass = (jclass) env->NewGlobalRef(subtitleStream),
      .subtitleCodecClass = (jclass) env->NewGlobalRef(subtitleCodec),
      .subtitleStreamConstructor = env->GetMethodID(subtitleStream, "<init>", "(IJJLjava/util/Map;Lcom/mediadevkit/sdk/MdkSubtitleCodec;)V"),
      .subtitleCodecConstructor = env->GetMethodID(subtitleCodec, "<init>", "(Ljava/lang/String;I)V"),
      .hashMapClass = (jclass) env->NewGlobalRef(hashMapClass),
      .hashMapConstructor = env->GetMethodID(hashMapClass, "<init>", "()V"),
      .hashMapPut = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
      .arrayList = (jclass) env->NewGlobalRef(arrayListClass),
      .arrayListConstructor = env->GetMethodID(arrayListClass, "<init>", "(I)V"),
      .arrayListAdd = env->GetMethodID(arrayListClass, "add", "(ILjava/lang/Object;)V"),
  };
}

static jobject buildMediaInfo(
    JNIEnv *env,
    mdk::MediaInfo info
) {
  
  auto mediaInfoDef = javaMediaInfo(env);
  auto videos = env->NewObject(mediaInfoDef.arrayList, mediaInfoDef.arrayListConstructor, (jint) info.video.size());
  auto audios = env->NewObject(mediaInfoDef.arrayList, mediaInfoDef.arrayListConstructor, (jint) info.audio.size());
  auto subtitles = env->NewObject(mediaInfoDef.arrayList, mediaInfoDef.arrayListConstructor, (jint) info.subtitle.size());


  for (int i = 0; i < info.video.size(); i++) {
    auto track = info.video[i];
    auto params = track.codec;
    auto metaData = env->NewObject(mediaInfoDef.hashMapClass, mediaInfoDef.hashMapConstructor);

    for (const auto& pair : track.metadata) {
      auto key = env->NewStringUTF(pair.first.c_str());
      auto value = env->NewStringUTF(pair.second.c_str());
      env->CallObjectMethod(metaData, mediaInfoDef.hashMapPut, key, value);
    }

    auto javaCodec = env->NewObject(
        mediaInfoDef.videoCodecClass,
        mediaInfoDef.videoCodecConstructor,
        /* codec = */ env->NewStringUTF(params.codec),
        /* codecTag = */ static_cast<jint>(params.codec_tag),
        /* bitRate = */ params.bit_rate,
        /* profile = */ params.profile,
        /* level = */ params.level,
        /* frameRate = */ params.frame_rate,
        /* format = */ params.format,
        /* formatName = */ env->NewStringUTF(params.format_name),
        /* width = */ params.width,
        /* height = */ params.height,
        /* bFrames = */ params.b_frames,
        /* par = */ params.par,
        /* colorSpace = */ (jint) params.color_space,
        /* dolbyVisionProfile = */ (jint) params.dovi_profile
    );
    auto javaStream = env->NewObject(
        mediaInfoDef.videoStreamClass,
        mediaInfoDef.videoStreamConstructor,
        /* index = */ track.index,
        /* startTime = */ track.start_time,
        /* duration = */ track.duration,
        /* frames = */ track.frames,
        /* rotation = */ track.rotation,
        /* metaData = */ metaData,
        /* codec = */ javaCodec
    );
    env->CallVoidMethod(videos, mediaInfoDef.arrayListAdd, i, javaStream);
  }

  for (int i = 0; i < info.subtitle.size(); i++) {
    auto track = info.subtitle[i];
    auto params = track.codec;
    auto metaData = env->NewObject(mediaInfoDef.hashMapClass, mediaInfoDef.hashMapConstructor);
    for (const auto& pair : track.metadata) {
      auto key = env->NewStringUTF(pair.first.c_str());
      auto value = env->NewStringUTF(pair.second.c_str());
      env->CallObjectMethod(metaData, mediaInfoDef.hashMapPut, key, value);
    }
    auto javaCodec = env->NewObject(
        /* clazz = */ mediaInfoDef.subtitleCodecClass,
        /* methodID = */ mediaInfoDef.subtitleCodecConstructor,
        /* codec = */ env->NewStringUTF(params.codec),
        /* codecTag = */ static_cast<jint>(params.codec_tag)
    );
    auto javaStream = env->NewObject(
        /* clazz = */ mediaInfoDef.subtitleStreamClass,
        /* methodID = */ mediaInfoDef.subtitleStreamConstructor,
        /* index = */ track.index,
        /* duration = */ track.duration,
        /* startTime = */ track.start_time,
        /* metaData = */ metaData,
        /* codec = */ javaCodec
    );
    env->CallVoidMethod(subtitles, mediaInfoDef.arrayListAdd, i, javaStream);
  }

  for (int i = 0; i < info.audio.size(); i++) {
    auto track = info.audio[i];
    auto params = track.codec;
    auto metaData = env->NewObject(mediaInfoDef.hashMapClass, mediaInfoDef.hashMapConstructor);
    for (const auto& pair : track.metadata) {
      auto key = env->NewStringUTF(pair.first.c_str());
      auto value = env->NewStringUTF(pair.second.c_str());
      env->CallObjectMethod(metaData, mediaInfoDef.hashMapPut, key, value);
    }
    auto javaCodec = env->NewObject(
        /* clazz = */ mediaInfoDef.audioCodecClass,
        /* methodID = */ mediaInfoDef.audioCodecConstructor,
        /* codec = */ env->NewStringUTF(params.codec),
        /* codecTag = */ static_cast<jint>(params.codec_tag),
        /* bitRate = */ params.bit_rate,
        /* profile = */ params.profile,
        /* level = */ params.level,
        /* frameRate = */ params.frame_rate,
        /* isFloat = */ params.is_float,
        /* isUnsigned = */ params.is_unsigned,
        /* isPlanar = */ params.is_planar,
        /* rawSampleSize = */ params.raw_sample_size,
        /* channels = */ params.channels,
        /* sampleRate = */ params.sample_rate,
        /* blockAlign = */ params.block_align,
        /* frameSize = */ params.frame_size
    );
    auto javaStream = env->NewObject(
        /* clazz = */ mediaInfoDef.audioStreamClass,
        /* methodID = */ mediaInfoDef.audioStreamConstructor,
        /* index = */ track.index,
        /* startTime = */ track.start_time,
        /* duration = */ track.duration,
        /* frames = */ track.frames,
        /* metaData = */ metaData,
        /* codec = */ javaCodec
    );
    env->CallVoidMethod(audios, mediaInfoDef.arrayListAdd, i, javaStream);
  }

  return env->NewObject(
      /* clazz = */ mediaInfoDef.mediaInfoClass,
      /* methodID = */ mediaInfoDef.mediaInfoConstructor,
      /* startTime = */ info.start_time,
      /* duration = */ info.duration,
      /* bitRate = */ info.bit_rate,
      /* size = */ info.size,
      /* format = */ env->NewStringUTF(info.format),
      /* streams = */ info.streams,
      /* audio = */ audios,
      /* video = */ videos,
      /* subtitles = */ subtitles
  );
}

extern "C" {

  JNIEXPORT jlong JNICALL
  Java_com_mediadevkit_sdk_LibMdk_newGlobalRef(
      JNIEnv *env,
      jclass clazz,
      jobject obj
  ) {
    if (obj == nullptr) return 0L;
    return (intptr_t) env->NewGlobalRef(obj);
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_deleteGlobalRef(
      JNIEnv *env,
      jclass clazz,
      jlong ref
  ) {
    if (ref == 0L) return;
    env->DeleteGlobalRef( (jobject) ref);
  }

  JNIEXPORT jlong JNICALL
  Java_com_mediadevkit_sdk_LibMdk_createPlayer(
      JNIEnv *env,
      jclass clazz
  ) {
    auto ref = new PlayerRef();
    return (jlong) ref;
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_setState(
      JNIEnv *env,
      jclass clazz,
      jlong handle,
      jint state
  ) {
    if (handle == 0L) return;
    auto player = get(handle);
    player->set( (mdk::State) state);
  }

  JNIEXPORT jint JNICALL
  Java_com_mediadevkit_sdk_LibMdk_getState(
      JNIEnv *env,
      jclass clazz,
      jlong handle
  ) {
    if (handle == 0L) return (jint) mdk::State::NotRunning;
    auto player = get(handle);
    return (jint) player->state();
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_setColorSpace(
      JNIEnv *env,
      jclass clazz,
      jlong handle,
      jlong surface,
      jint color_space
  ) {
    if (handle == 0L) return;
    auto player = get(handle);
    player->set( (mdk::ColorSpace) color_space, surface == 0L ? nullptr : (void*) surface);
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_setMedia(
      JNIEnv *env,
      jclass clazz,
      jlong handle,
      jstring url
  ) {
    if (handle == 0L) return;
    auto player = get(handle);
    if (url == nullptr) {
      player->setMedia(nullptr);
    } else {
      auto cUrl = env->GetStringUTFChars(url, nullptr);
      player->setMedia(cUrl);
      env->ReleaseStringUTFChars(url, cUrl);
    }
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_release(
      JNIEnv *env,
      jclass clazz,
      jlong handle
  ) {
    if (handle == 0L) return;
    delete (PlayerRef*) handle;
  }

  JNIEXPORT jlong JNICALL
  Java_com_mediadevkit_sdk_LibMdk_getPosition(
      JNIEnv *env,
      jclass clazz,
      jlong handle
  ) {
    if (handle == 0L) return 0L;
    auto player = get(handle);
    return player->position();
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_seek(
      JNIEnv *env,
      jclass clazz,
      jlong handle,
      jlong position
  ) {
    if (handle == 0L) return;
    auto player = get(handle);
    player->seek(position);
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_setProperty(
      JNIEnv *env,
      jclass clazz,
      jlong handle,
      jstring key,
      jstring value
  ) {
    if (handle == 0L) return;
    auto player = get(handle);
    auto cKey = env->GetStringUTFChars(key, nullptr);
    auto cValue = env->GetStringUTFChars(value, nullptr);
    player->setProperty(cKey, cValue);
    env->ReleaseStringUTFChars(key, cKey);
    env->ReleaseStringUTFChars(value, cValue);
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_updateNativeSurface(
      JNIEnv *env,
      jclass clazz,
      jlong handle,
      jlong surface,
      jint width,
      jint height,
      jint type
  ) {
    if (handle == 0L) return;
    log_info("player->updateNativeSurface handle=%lld, surface=%lld, width=%d, height=%d, type=%d", handle, surface, width, height, type);
    auto player = get(handle);
    player->updateNativeSurface(
        /* surface = */ surface == 0L ? nullptr : (jobject) surface,
        /* width = */ width,
        /* height = */  height,
        /* type = */ (mdk::Player::SurfaceType) type
    );
    log_info("done");
  }


  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_setListener(
      JNIEnv *env,
      jclass clazz,
      jlong handle,
      jobject listener
  ) {
    if (handle == 0L) return;
    auto player = get(handle);
    auto weak = env->NewWeakGlobalRef(listener);
    auto onMediaStatusMethod = env->GetMethodID(env->GetObjectClass(listener),"onMediaStatus","(II)V");
    auto onStateMethod = env->GetMethodID(env->GetObjectClass(listener),"onState","(I)V");
    player->onMediaStatus(
        /* cb = */ [weak, onMediaStatusMethod] (mdk::MediaStatus prev, mdk::MediaStatus next) {
          auto env = jmi::getEnv();
          auto isValid = !env->IsSameObject(weak, nullptr);
          if (!isValid) return false;
          env->CallVoidMethod(weak, onMediaStatusMethod, prev, next);
          return true;
        },
        /* token = */ nullptr
    );
    player->onStateChanged(
        /* cb = */ [weak, onStateMethod] (mdk::State state) {
          auto env = jmi::getEnv();
          auto isValid = !env->IsSameObject(weak, nullptr);
          if (!isValid) return false;
          env->CallVoidMethod(weak, onStateMethod, state);
          return true;
        }
    );
  }


  JNIEXPORT jobject JNICALL
  Java_com_mediadevkit_sdk_LibMdk_getMediaInfo(
      JNIEnv *env,
      jclass clazz,
      jlong handle
  ) {
    if (handle == 0L) return nullptr;
    auto player = get(handle);
    return buildMediaInfo(env, player->mediaInfo());
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_setGlobalOptionString(
      JNIEnv *env,
      jclass clazz,
      jstring key,
      jstring value
  ) {
    auto cKey = env->GetStringUTFChars(key, nullptr);
    auto cValue = env->GetStringUTFChars(value, nullptr);
    SetGlobalOption(cKey, cValue);
    env->ReleaseStringUTFChars(key, cKey);
    env->ReleaseStringUTFChars(value, cValue);
  }

  JNIEXPORT void JNICALL
  Java_com_mediadevkit_sdk_LibMdk_setGlobalOptionInt(
      JNIEnv *env,
      jclass clazz,
      jstring key,
      jint value
  ) {
    auto cKey = env->GetStringUTFChars(key, nullptr);
    SetGlobalOption(cKey, value);
    env->ReleaseStringUTFChars(key, cKey);
  }

}


