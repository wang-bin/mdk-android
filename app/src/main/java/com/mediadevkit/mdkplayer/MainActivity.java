package com.mediadevkit.mdkplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.GestureDetector;
import android.view.WindowManager;
import android.view.SurfaceView;
import android.view.View;
import android.util.Log;

// TODO: render to SurfaceTexture surface. OnFrameAvailable will be called after swapBuffers or data copied? http://blog.csdn.net/king1425/article/details/72773331
// TODO: request permissions(sdcard) for android 23+
public class MainActivity extends AppCompatActivity /*AppCompatActivity*/{
    private SurfaceView mView = null;
    private MDKPlayer mPlayer = null;
    private VelocityTracker mVelocityTracker = null;
    private int mState = 0;
    private int mPos = 0;
    private float mX = 0;
    final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPlayer = new MDKPlayer();
        if (true) {
            mView = new SurfaceView(this); // FIXME: destroyed on pause
        } else {
            // background error:
//09-30 23:26:30.337	17378	17403	E	Surface	queueBuffer: error queuing buffer to SurfaceTexture, -19
//09-30 23:26:30.337	17378	17403	I	Adreno	QueueBuffer: queueBuffer failed
//09-30 23:26:30.337	17378	17403	W	GLThread	eglSwapBuffers failed: EGL_BAD_SURFACE
//09-30 23:26:30.427	17378	17378	W	IInputConnectionWrapper	reportFullscreenMode on inexistent InputConnection
            GLSurfaceView glView = new GLSurfaceView(this);
            // Pick an EGLConfig with RGB8 color, 16-bit depth, no stencil,
            // supporting OpenGL ES 2.0 or later backwards-compatible versions.
            glView.setEGLConfigChooser(5, 6, 5, 0, 0, 0);
            glView.setEGLContextClientVersion(2);
            glView.setRenderer(new DemoRenderer(mPlayer));
            mView = glView;
        }
        setContentView(mView);
        mPlayer.setSurfaceView(mView);

        Intent intent = getIntent();
        String action = intent.getAction();
        if (intent.ACTION_VIEW.equals(action)) {
            mPlayer.setMedia(intent.getDataString());
        } else {
            // getExternalFilesDir(Environment.DIRECTORY_MOVIES).toString() // app local
            mPlayer.setMedia(Environment.getExternalStorageDirectory().toString() + "/Movies/newyear.mp4");
            String[] urls = new String[15];
            for (int i = 0; i < 10; ++i)
                urls[i] = "/sdcard/Movies/s/s0" + i + ".mkv";
            for (int i = 10; i < 15; ++i)
                urls[i] = "/sdcard/Movies/s/s" + i + ".mkv";
            //mPlayer.setPlayList(urls);
        }
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        gestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener(){
            public boolean onDoubleTap(MotionEvent e) {
                //Log.i("MDK.Java","onDoubleTap e.getX(): " + e.getX());
                if (mPlayer.state() == 1)
                    mPlayer.setState(2);
                else
                    mPlayer.setState(1);
                return true;
            }
            public boolean onDoubleTapEvent(MotionEvent e) { return false;}
            public boolean onSingleTapConfirmed(MotionEvent e) {return false;}
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        //mView.setVisibility(View.GONE);
        mPlayer.setState(2);
        // if not set null here, surface holder will be destroyed too, but SurfaceHolder store in player class will not change when calling mPlayer.setSurfaceView(mView) in onResume() and surface is not updated in native
        mPlayer.setSurfaceView(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //mView.setVisibility(View.VISIBLE);
        mPlayer.setState(1);
        mPlayer.setSurfaceView(mView); // ensure vo is created
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mX = event.getX();
                mPos = mPlayer.position();
                mState = mPlayer.state();
                if (mState == 0)
                    mState = 1;
                //Log.i("MDK.Java","DOWN event.getX(): " + event.getX() + " mPos:" + mPos);
                mPlayer.setState(2);
                if (mVelocityTracker == null)
                    mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.clear();
                mVelocityTracker.addMovement(event);
            }
            break;
            case MotionEvent.ACTION_MOVE: {
                mVelocityTracker.addMovement(event);
                mVelocityTracker.computeCurrentVelocity(1000);
                float dx = event.getX() - mX;
                if (dx > 20) { // TODO: depends on duration and screen size, not velocity
                    mPos += 1000;
                    mX = event.getX();
                } else if (dx < -20) {
                    mPos -= 1000;
                    mX = event.getX();
                }
                //Log.i("MDK.Java","MOVE event.getX(): " + event.getX() + " mPos:" + mPos);
                if (mPos > 0)
                    mPlayer.seek(mPos);
                //Log.i("MDK.Java","velocityTraker: "+mVelocityTracker.getXVelocity());
            }
            break;
            case MotionEvent.ACTION_UP:
                mPlayer.setState(mState);
                //Log.i("MDK.Java","UP event.getX(): " + event.getX());
                break;
            case MotionEvent.ACTION_CANCEL:
                mVelocityTracker.recycle();
                break;
            default:
                break;
        }
        return true;
    }
    static class DemoRenderer implements GLSurfaceView.Renderer {
        private MDKPlayer mPlayer;
        DemoRenderer(MDKPlayer player) {
            mPlayer = player;
        }
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        }
        public void onSurfaceChanged(GL10 gl, int w, int h) {
            mPlayer.resizeVideoSurface(w, h);
        }
        public void onDrawFrame(GL10 gl) {
            mPlayer.renderVideo();
        }
    }
}
