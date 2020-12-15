package com.mediadevkit.mdkplayer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.os.Looper;
import android.os.Handler;
import android.os.Message;
import java.lang.ref.WeakReference;

public class MDKPlayer implements SurfaceHolder.Callback {
    public Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            Log.i("mdk.MDKPlayer", "handleMessage " + msg);
        }
    };

    private static void postEventFromNative(Object tgt, int what, int arg1, int arg2, Object obj) {
        Log.i("mdk.MDKPlayer", tgt + " postEventFromNative " + obj);
        //MDKPlayer mp = (MDKPlayer)((WeakReference<?>)tgt).get();
        MDKPlayer mp = (MDKPlayer)tgt;
        if (mp == null)
            return;
        if (mp.mHandler != null) {
            Message msg = mp.mHandler.obtainMessage(what, arg1, arg2, obj);
            msg.sendToTarget();
        }
    }

    public MDKPlayer() { native_ptr = nativeCreate(); }
    public void setMedia(String url) { nativeSetMedia(native_ptr, url); }
    public void setNextMedia(String url) { nativeSetNextMedia(native_ptr, url); }
    public void setPlayList(String[] urls) { nativeSetPlayList(native_ptr, urls); }

    //public void setPreloadNextImmediately(bool value) { nativeSetPreloadNextImmediately(native_ptr, value); }
    void setState(int state) { nativeSetState(native_ptr, state); }
    int state() {return nativeState(native_ptr);}
    void resizeVideoSurface(int width, int height) { nativeResizeVideoSurface(native_ptr, width, height);}
    void renderVideo() { nativeRenderVideo(native_ptr);}
    void seek(int ms) { nativeSeek(native_ptr, ms);}
    int position() { return nativePosition(native_ptr);}

    int getDuration() { return nativeGetDuration(native_ptr); }

    protected void finalize() {
        nativeDestroy(native_ptr);
        native_ptr = 0;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        native_win = setSurface(holder.getSurface(), width, height);
        Log.i("mdk.MDKPlayer", "surfaceChanged. native_win: " + native_win + " player: " + native_ptr);
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        setSurface(null, 0, 0); // if surfaceDestroyed() was not called
        native_win = setSurface(holder.getSurface(), -1, -1);
        Log.i("mdk.MDKPlayer", Thread.currentThread() + " surface string: " + holder.getSurface().toString() + " surfaceCreated. native_win: " + native_win + " player: " + native_ptr);
    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i("mdk.MDKPlayer", "surfaceDestroyed. native_win: " + native_win + " player: " + native_ptr);
        if (native_win == 0)
            return;
        native_win = setSurface(null, 0, 0);
        holder.removeCallback(this);
    }

    void setSurfaceView(SurfaceView sv) {
        if (sv instanceof GLSurfaceView) {

        } else {
            if (sv == null)
                setSurfaceHolder(null);
            else
                setSurfaceHolder(sv.getHolder());
        }
    }
    void setSurfaceHolder(SurfaceHolder holder) {
        if (sh == holder)
            return;
        sh = holder;
        if (sh != null)
            sh.addCallback(this);
    }
    private long setSurface(Surface surface, int w, int h) {
        if (native_ptr == 0)
            return 0;
        return nativeSetSurface(native_ptr, surface, native_win, w, h);
    }

    private long native_ptr;
    private long native_win;
    private SurfaceHolder sh;
    private native long nativeCreate();
    private native void nativeDestroy(long obj_ptr);
    private native void nativeSetMedia(long obj_ptr, String url);
    private native void nativeSetNextMedia(long obj_ptr, String url);
    private native void nativeSetPlayList(long obj_ptr, String[] urls);

    //private native void nativeSetPreloadNextImmediately(long obj_ptr, boolean value);
    private native void nativeSetState(long obj_ptr, int state);
    private native int nativeState(long obj_ptr);
    private native void nativeResizeVideoSurface(long obj_ptr, int width, int height);
    private native void nativeRenderVideo(long obj_ptr);

    private native long nativeSetSurface(long obj_ptr, Surface surface, long win, int w, int h);

    private native int nativeGetDuration(long obj_ptr);
    private native int nativePosition(long obj_ptr);
    private native void nativeSeek(long obj_ptr, int msec);

    static {
        // android 4.2 linker can not load dependencies in apk, so manually load them
        try {
            System.loadLibrary("c++_shared");
        } catch(UnsatisfiedLinkError e) {}
        try {
            System.loadLibrary("gnustl_shared");
        } catch(UnsatisfiedLinkError e) {}
        System.loadLibrary("mdk");
        try {
            System.loadLibrary("mdk-avglue");
        } catch(UnsatisfiedLinkError e) {}
        System.loadLibrary("mdk-jni");
    }
}