package com.mediadevkit.mdkplayer;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;

import com.mediadevkit.sdk.MDKPlayer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import android.graphics.ColorSpace;
import android.widget.Switch;

// TODO: render to SurfaceTexture surface. OnFrameAvailable will be called after swapBuffers or data copied? http://blog.csdn.net/king1425/article/details/72773331
// TODO: request permissions(sdcard) for android 23+
// AppCompatActivity:  incompatible with android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen" // https://stackoverflow.com/questions/39604889/how-to-fix-you-need-to-use-a-theme-appcompat-theme-or-descendant-with-this-a/39604946
public class MainActivity extends AppCompatActivity {
    private SurfaceView mView = null;
    private GLSurfaceView mGLView = null;
    private MDKPlayer mPlayer = null;
    private boolean mIsFullscreen = false;
    private SeekBar mSeekBar = null;
    private boolean mIsSeeking = false;
    private final Handler mProgressHandler = new Handler(android.os.Looper.getMainLooper());
    private final Runnable mProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (mPlayer != null && mSeekBar != null && !mIsSeeking) {
                int duration = mPlayer.getDuration();
                if (duration > 0) {
                    if (mSeekBar.getMax() != duration)
                        mSeekBar.setMax(duration);
                    mSeekBar.setProgress(mPlayer.position());
                }
            }
            mProgressHandler.postDelayed(this, 500);
        }
    };
    final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener());
    private ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    // Handle the returned Uri
                    if (uri == null)
                        return;
                    Log.i("MDK.java", "mGetContent: " + uri.toString());
                    mPlayer.setState(0);
                    mPlayer.setNextMedia(null);
                    mPlayer.setMedia(uri.toString());
                    mPlayer.setState(1);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //getWindow().setColorMode(ActivityInfo.COLOR_MODE_HDR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i("MDK.Java","getColorMode(): " + getWindow().getColorMode());
        }
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE); //It's enough to remove the line
        //But if you want to display  full screen (without action bar) write too
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        mPlayer = new MDKPlayer();
        mView = findViewById(R.id.surfaceView);
        mPlayer.setSurfaceView(mView);
        findViewById(R.id.Open).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mGetContent.launch("video/*");
            }
        });
        Button playStopBtn = findViewById(R.id.PlayStop);
        playStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playStopBtn.setText(mPlayer.state() == 0 ? "Play" : "Stop");
                mPlayer.setState(mPlayer.state() == 0 ? 1 : 0);
            }
        });
        Switch hdr = findViewById(R.id.HDR);
        hdr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean isChecked){
                if (isChecked)
                    mPlayer.setColorSpace(0);
                else
                    mPlayer.setColorSpace(1);
            }
        });
        hdr.setChecked(true);
        Spinner audioBackendSpinner = findViewById(R.id.audioBackendSpinner);
        audioBackendSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mPlayer.setAudioBackend((String) parent.getItemAtPosition(position));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        mSeekBar = findViewById(R.id.seekBar);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsSeeking = true;
            }
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mPlayer.seek(seekBar.getProgress());
                mIsSeeking = false;
            }
        });
/*
        mGLView = findViewById(R.id.glSurfaceView);
        mGLView.setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        mGLView.setEGLContextClientVersion(2);
        mGLView.setRenderer(new DemoRenderer(mPlayer));
*/
        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            mPlayer.setMedia(intent.getDataString());
        } else {
            // getExternalFilesDir(Environment.DIRECTORY_MOVIES).toString() // app local
            //mPlayer.setMedia(Environment.getExternalStorageDirectory().toString() + "/Movies/Samsung Chasing The Light Demo.ts");
            //mPlayer.setMedia("https://live.nodemedia.cn:8443/live/b480_265.flv");
            //mPlayer.setMedia("http://192.168.3.168:8888/86831_2158.ts");
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
                mIsFullscreen = !mIsFullscreen;
                setRequestedOrientation(mIsFullscreen
                        ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                return true;
            }
            public boolean onDoubleTapEvent(@NonNull MotionEvent e) { return false;}
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mPlayer.state() == 1)
                    mPlayer.setState(2);
                else
                    mPlayer.setState(1);
                return true;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mProgressHandler.removeCallbacks(mProgressUpdater);
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
        mProgressHandler.post(mProgressUpdater);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        gestureDetector.onTouchEvent(event);
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
