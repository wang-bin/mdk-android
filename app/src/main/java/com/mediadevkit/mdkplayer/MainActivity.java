package com.mediadevkit.mdkplayer;

import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.VelocityTracker;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.mediadevkit.sdk.MDKPlayer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// TODO: render to SurfaceTexture surface. OnFrameAvailable will be called after swapBuffers or data copied? http://blog.csdn.net/king1425/article/details/72773331
// TODO: request permissions(sdcard) for android 23+
// AppCompatActivity:  incompatible with android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen" // https://stackoverflow.com/questions/39604889/how-to-fix-you-need-to-use-a-theme-appcompat-theme-or-descendant-with-this-a/39604946
public class MainActivity extends AppCompatActivity /*AppCompatActivity*/{
    private SurfaceView mView = null;
    private GLSurfaceView mGLView = null;
    private MDKPlayer mPlayer = null;
    private VelocityTracker mVelocityTracker = null;
    private int mState = 0;
    private int mPos = 0;
    private float mX = 0;
    final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE); //It's enough to remove the line
        //But if you want to display  full screen (without action bar) write too
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        mPlayer = new MDKPlayer();
        mView = findViewById(R.id.surfaceView);
        mPlayer.setSurfaceView(mView);

        mGLView = findViewById(R.id.glSurfaceView);
        mGLView.setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        mGLView.setEGLContextClientVersion(2);
        mGLView.setRenderer(new DemoRenderer(mPlayer));

        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            mPlayer.setMedia(intent.getDataString());
        } else {
            // getExternalFilesDir(Environment.DIRECTORY_MOVIES).toString() // app local
            //mPlayer.setMedia(Environment.getExternalStorageDirectory().toString() + "/Movies/Samsung Chasing The Light Demo.ts");
            //mPlayer.setMedia("https://live.nodemedia.cn:8443/live/b480_265.flv");
            //mPlayer.setMedia("http://192.168.3.168:8888/86831_2158.ts");
            mPlayer.setMedia("https://vfx.mtime.cn/Video/2021/11/16/mp4/211116131456748178.mp4");//https://ks3-cn-beijing.ksyun.com/ksplayer/h265/mp4_resource/jinjie_265.mp4");
            //mPlayer.setMedia("https://www.rmp-streaming.com/media/big-buck-bunny-720p.mp4");
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
                //mPlayer.setState(2);
                if (mState == 1)
                    mPlayer.setState(2);
                else
                    mPlayer.setState(1);
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
                //mPlayer.setState(mState);
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
        private final MDKPlayer mPlayer;
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
