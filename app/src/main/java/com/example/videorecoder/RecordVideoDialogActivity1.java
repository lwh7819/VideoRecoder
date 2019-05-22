package com.example.videorecoder;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.videorecoder.R;
import com.example.videorecoder.SupportedSizesReflect;
import com.example.videorecoder.ToastUtils;
import com.example.videorecoder.VideoUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 自定义视频录制
 */
public class RecordVideoDialogActivity1 extends AppCompatActivity implements View.OnClickListener {
    public static final String CUSTOM_VIDEO_PATH = "video_path";
    private static final int MODEL_DEFAULT = 0;
    private static final int MODEL_RECORDING = 1;
    private static final int MODEL_RECORD_PAUSE = 2;
    private static final int MODEL_RECORD_END = 3;
    private static final int MODEL_PLAYING = 4;
    private static final int MODEL_PLAY_PAUSE = 5;
    private static final int MODEL_PLAY_END = 6;

    private SurfaceView svVideo;
    private ImageView ivPlay;
    private ImageView ivRecord;
    private TextView tvVideoTime;
    private TextView tvReRecord;
    private TextView tvConfirm;
    private TextView tvCancel;

    private MediaRecorder mRecorder;
    private Camera mCamera;
    private SurfaceHolder mSurfaceHolder;

    private int mCurModel;
    private long mStartTime;
    private boolean mCanUseRecord = true;
    private boolean isClosed;
    private boolean mRecordIsStopping;
    private ImageView ivChangeVideo;
    private ImageView ivStartStop;

    int cameraCount = 0;
    boolean isCameraBack = true;
    private int cameraPosition = 1;// 0代表前置摄像头，1代表后置摄像头

    private String currentVideoFilePath;
    private String saveVideoPath = "";
    private int currentRecordSecond = 0;
    private int startRecordSecond = 0;
    private MergeFileAsyncTask asyncTask;
    private OrientationEventListener mOrientationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            Window window = getWindow();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // 保持屏幕唤醒状态
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.setStatusBarColor(Color.TRANSPARENT);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // 设置状态栏透明
                window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_record_video);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO}, 1);
            }
        }
        init();
        startOrientationChangeListener();
    }

    private boolean mScreenPortrait = true;

    /**
     * 监听平板是横着的还是竖着的（并不是横屏竖屏）
     */
    private final void startOrientationChangeListener() {
        mOrientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int rotation) {
                if (((rotation >= 0) && (rotation <= 45)) || (rotation >= 315) || ((rotation >= 135) && (rotation <= 225))) {//portrait
                    if (true != mScreenPortrait) {
                        mScreenPortrait = true;
                        Log.d("Orientation", "Screen orientation changed from Landscape to Portrait!");
                    }
                } else if (((rotation > 45) && (rotation < 135)) || ((rotation > 225) && (rotation < 315))) {//landscape
                    if (false != mScreenPortrait) {
                        mScreenPortrait = false;
                        Log.d("Orientation", "Screen orientation changed from Portrait to Landscape!");
                    }
                }
            }
        };
        mOrientationListener.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCurModel == MODEL_RECORDING) {
            pauseRecordVideo();
            mCamera.lock();
            setProgressText(0);
            ivStartStop.setVisibility(View.GONE);
            ivRecord.setVisibility(View.GONE);
            tvReRecord.setVisibility(View.VISIBLE);
            currentRecordSecond = 0;
            startRecordSecond = 0;
            mCurModel = MODEL_RECORD_END;
        } else if (mCurModel == MODEL_RECORD_PAUSE) {
            mCurModel = MODEL_RECORD_END;
            mCamera.lock();
            setProgressText(0);
            ivStartStop.setVisibility(View.GONE);
            ivRecord.setVisibility(View.GONE);
            tvReRecord.setVisibility(View.VISIBLE);
            currentRecordSecond = 0;
            startRecordSecond = 0;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRecorder != null) {
            // 释放资源
            mRecorder.release();
            mRecorder = null;
        }

        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.lock();
            mCamera.release();
            mCamera = null;
        }

        if (mHandler != null) {
            mHandler.removeCallbacks(recordProgressTextTask);
            mHandler = null;
        }
        if (asyncTask != null) {
            if (!asyncTask.isCancelled()) {
                asyncTask.cancel(true);
            }
            asyncTask = null;
        }
        if (mOrientationListener != null) {
            mOrientationListener.disable();
            mOrientationListener = null;
        }
        recordProgressTextTask = null;
        isClosed = true;
    }

    private void init() {
        mHandler = new Handler(Looper.getMainLooper());
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isClosed)
                    return;
                mCanUseRecord = true;
            }
        }, 1500);

        initView();
        isClosed = false;
    }

    private void initView() {
        ivPlay = findViewById(R.id.ivPlay);
        ivPlay.setVisibility(View.GONE);
        ivPlay.setOnClickListener(this);
        ivRecord = findViewById(R.id.ivRecord);
        ivRecord.setOnClickListener(this);
        ivRecord.setVisibility(View.VISIBLE);
        tvVideoTime = findViewById(R.id.tvVideoTime);
        setProgressText(0);
        tvReRecord = findViewById(R.id.tvReRecord);
        tvReRecord.setOnClickListener(this);
        tvReRecord.setVisibility(View.GONE);
        tvConfirm = findViewById(R.id.tvConfirm);
        tvConfirm.setOnClickListener(this);
        tvCancel = findViewById(R.id.tvCancel);
        tvCancel.setOnClickListener(this);
        ivChangeVideo = findViewById(R.id.ivChangeVideo);
        ivChangeVideo.setOnClickListener(this);
        ivStartStop = findViewById(R.id.ivStartStop);
        ivStartStop.setOnClickListener(this);

        svVideo = findViewById(R.id.svVideo);
        mSurfaceHolder = svVideo.getHolder();
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceHolder = holder;
                onSurfaceCreate();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d("TAG", "RecordVideoDialogActivity->surfaceChanged(): " + holder.toString());
                mSurfaceHolder = holder;
                try {
                    if (mCamera != null) {
                        if (mCurModel != MODEL_PLAYING && mCurModel != MODEL_PLAY_PAUSE && mCurModel != MODEL_PLAY_END) {
                            mCamera.setPreviewDisplay(mSurfaceHolder);
                            mCamera.startPreview();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    ToastUtils.shortShow(RecordVideoDialogActivity1.this,"相机无法使用");
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d("TAG", "RecordVideoDialogActivity->surfaceDestroyed(): " + holder.toString());
                if (mCamera != null) {
                    mCamera.stopPreview();
                }
            }
        });
    }

    /**
     * 开启摄像头并绑定surfaceHolder并开启预览
     */
    private void onSurfaceCreate() {
        try {
            if (cameraPosition == 1) {
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            } else {
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }
            mCamera = deal(mCamera);
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建MediaPlayer
     */
    private void initRecord() {
        // 创建MediaPlayer对象
        mRecorder = new MediaRecorder();
    }

    private long latestClickTime;

    @Override
    public void onClick(View v) {
        if (System.currentTimeMillis() - latestClickTime < 1000)
            return;
        latestClickTime = System.currentTimeMillis();
        if (mRecordIsStopping) {
            ToastUtils.shortShow(this,"录制关闭中，暂时无法进行其他操作");
            return;
        }
        int i = v.getId();
        if (i == R.id.tvReRecord) {
            if (mCurModel >= MODEL_RECORD_END) {
                reRecordVideo();
            }
        } else if (i == R.id.ivPlay) {
            if (mCurModel == MODEL_RECORD_END) {
                playVideo();
            }
        } else if (i == R.id.ivRecord) {
            ivChangeVideo.setVisibility(View.GONE);
            if (mCurModel == MODEL_DEFAULT) {
                if (mCanUseRecord) {
                    try {
                        currentVideoFilePath = getSDPath() + getVideoName();
                        Log.e("lwc", "start" + currentVideoFilePath);
                        startRecordVideo();
                    } catch (Exception e) {
                        ToastUtils.shortShow(this,"相机无法使用，请稍候再试");
                        e.printStackTrace();
                    }
                } else {
                    ToastUtils.shortShow(this,"录制准备中，请稍后再试");
                }
            } else if (mCurModel == MODEL_RECORDING) {
                currentRecordSecond = 0;
                startRecordSecond = 0;
                ivStartStop.setVisibility(View.GONE);
                stopRecordVideo();
                //判断是否进行视频合并
                if ("".equals(saveVideoPath)) {
                    saveVideoPath = currentVideoFilePath;
                } else {
                    mergeRecordVideoFile();
                }
            } else if (mCurModel == MODEL_RECORD_PAUSE) {
                currentRecordSecond = 0;
                startRecordSecond = 0;
                ivStartStop.setVisibility(View.GONE);
                if (mCamera != null) {
                    mCamera.stopPreview();
                    try {
                        mCamera.setPreviewDisplay(null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ivPlay.setVisibility(View.VISIBLE);
                    ivPlay.setImageResource(R.mipmap.bt_play_square);
                    ivRecord.setVisibility(View.GONE);
                    tvReRecord.setVisibility(View.VISIBLE);
                    tvConfirm.setVisibility(View.VISIBLE);
                    tvCancel.setVisibility(View.VISIBLE);
                    mCurModel = MODEL_RECORD_END;
                }
            }
        } else if (i == R.id.ivChangeVideo) {
            changeVideo();
        } else if (i == R.id.ivStartStop) {
            if (mCurModel == MODEL_RECORDING) {
                startRecordSecond = currentRecordSecond;
                ivStartStop.setVisibility(View.VISIBLE);
                pauseRecordVideo();
            } else if (mCurModel == MODEL_RECORD_PAUSE) {
                currentVideoFilePath = getSDPath() + getVideoName();
                Log.e("lwc", "restart" + currentVideoFilePath);
                setConfigRecord();
                ivStartStop.setBackgroundResource(R.mipmap.stop);
                startRecordProgress();
                mCurModel = MODEL_RECORDING;
            }
        } else if (i == R.id.tvConfirm) {
            if (mCurModel < MODEL_RECORD_END)
                return;
            Intent intent = new Intent();
            intent.putExtra(CUSTOM_VIDEO_PATH, saveVideoPath);
            setResult(RESULT_OK, intent);
            finish();
        } else if (i == R.id.tvCancel) {
            finish();
        }
    }

    /**
     * 暂停录制视频
     */
    public void pauseRecordVideo() {
        long hasRecordTime = System.currentTimeMillis() - mStartTime;
        if (hasRecordTime < 1500) {
            mRecordIsStopping = true;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    pauseRecordVideo();
                    mRecordIsStopping = false;
                }
            }, 1500 - hasRecordTime);
            return;
        }
        // 设置后不会崩
        mRecorder.setOnErrorListener(null);
        mRecorder.setPreviewDisplay(null);
        //停止录制
        mRecorder.stop();
        mRecorder.reset();
        //释放资源
        mRecorder.release();
        mRecorder = null;

        ivStartStop.setBackgroundResource(R.mipmap.start);
        stopProgress();
        //判断是否进行视频合并
        if ("".equals(saveVideoPath)) {
            saveVideoPath = currentVideoFilePath;
        } else {
            mergeRecordVideoFile();
        }
        mCurModel = MODEL_RECORD_PAUSE;
    }

    /**
     * 结束录制
     */
    private void stopRecordVideo() {
        //如果录制时间过短，不能马上调用stop()方法。可能会导致崩溃
        long hasRecordTime = System.currentTimeMillis() - mStartTime;
        if (hasRecordTime < 1500) {
            mRecordIsStopping = true;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopRecordVideo();
                    mRecordIsStopping = false;
                }
            }, 1500 - hasRecordTime);
            return;
        }

        if (mRecorder != null && mCurModel == MODEL_RECORDING) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
            mCanUseRecord = false;
            mCamera.lock();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCanUseRecord = true;
                }
            }, 2500);
        }

        if (mCamera != null) {
            mCamera.stopPreview();
            try {
                mCamera.setPreviewDisplay(null);
            } catch (IOException e) {
                e.printStackTrace();
            }
            ivPlay.setVisibility(View.VISIBLE);
            ivPlay.setImageResource(R.mipmap.bt_play_square);
            ivRecord.setVisibility(View.GONE);
            tvReRecord.setVisibility(View.VISIBLE);
            tvConfirm.setVisibility(View.VISIBLE);
            tvCancel.setVisibility(View.VISIBLE);
            mCurModel = MODEL_RECORD_END;
        }
        stopProgress();
    }

    /**
     * 播放视频
     */
    private void playVideo() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String path = saveVideoPath;
        File file = new File(path);
        String packageName = getApplicationContext().getPackageName();
        Uri photoURI = FileProvider.getUriForFile(this, packageName + ".FileProvider", file);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(photoURI, "video/*");
        startActivity(intent);
    }

    /**
     * 重新录制
     */
    private void reRecordVideo() {
        currentRecordSecond = 0;
        startRecordSecond = 0;
        saveVideoPath = "";
        ivChangeVideo.setVisibility(View.VISIBLE);
        tvConfirm.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);
        initRecord();
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
            ToastUtils.shortShow(this,"录制异常，请退出重试");
            return;
        }
        mCamera.startPreview();
        mCurModel = MODEL_DEFAULT;
        ivPlay.setVisibility(View.GONE);
        ivRecord.setVisibility(View.VISIBLE);
        ivRecord.setImageResource(R.mipmap.record_start);
        tvReRecord.setVisibility(View.GONE);
        setProgressText(0);
    }

    private void setProgressText(int cur) {
        tvVideoTime.setText(formatTime(cur));
    }

    /**
     * 录制视频
     */
    private void startRecordVideo() {
        //正在录制中，就屏蔽多余的调用
        if (mCurModel == MODEL_RECORDING)
            return;
        if (mCamera == null) {
            return;
        }
        if (!Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            ToastUtils.shortShow(this,"SD卡不存在，请插入SD卡！");
            return;
        }

        setConfigRecord();
        mCurModel = MODEL_RECORDING;
        startRecordProgress();
        tvCancel.setVisibility(View.GONE);
        ivRecord.setImageResource(R.mipmap.record_end);
    }

    private void setConfigRecord() {
        initRecord();
        mCamera.unlock();
        mRecorder.setCamera(mCamera);
        // 设置从麦克风采集声音(或来自录像机的声音AudioSource.CAMCORDER)
        mRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        // 设置从摄像头采集图像
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        // 设置输出文件
        mRecorder.setOutputFile(currentVideoFilePath);

        // 设置视频文件的输出格式
        // 必须在设置声音编码格式、图像编码格式之前设置
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        // 设置声音编码的格式
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        // 设置图像编码的格式
        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
            CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
            // 设置视频分辨率
            if (cameraPosition == 1) {
                mRecorder.setVideoSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
            } else {
                mRecorder.setVideoSize(1280, 720);
            }
            // 每秒帧
            mRecorder.setVideoFrameRate(30);
        }
        // 设置比特率
        mRecorder.setVideoEncodingBitRate(1536 * 1024); // 1.5 * 1024 * 1024

        mRecorder.setMaxDuration(60 * 60 * 1000);

        //设置相机的横竖屏幕
        if (mScreenPortrait) {
            Log.e("orientation", "portrait");
            if (cameraPosition == 1) {
                mRecorder.setOrientationHint(90);
            } else {
//            mRecorder.setOrientationHint(270); // 2 3代
                mRecorder.setOrientationHint(90); // 4代
            }
        } else {
            Log.e("orientation", "landscape");
            if (cameraPosition == 1) {
                mRecorder.setOrientationHint(0);
            } else {
//            mRecorder.setOrientationHint(270); // 2 3代
                mRecorder.setOrientationHint(180); // 4代
            }
        }
        mRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    ToastUtils.shortShow(RecordVideoDialogActivity1.this, "录像已达最大时长");
                    mRecorder.stop();
                    mRecorder.release();
                    mRecorder = null;
                    mCanUseRecord = false;
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (isClosed)
                                return;
                            mCanUseRecord = true;
                        }
                    }, 2500);
                    mCamera.stopPreview();
                    try {
                        mCamera.setPreviewDisplay(null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mCurModel = MODEL_RECORD_END;
                    stopProgress();
                    ivPlay.setVisibility(View.VISIBLE);
                    ivPlay.setImageResource(R.mipmap.bt_play_square);
                    ivRecord.setVisibility(View.GONE);
                    tvReRecord.setVisibility(View.VISIBLE);
                }
            }
        });
        try {
            mRecorder.prepare();
        } catch (Exception e) {
            ToastUtils.shortShow(this,"录像失败");
            e.printStackTrace();
            mCurModel = MODEL_DEFAULT;
            mCamera.lock();
            mRecorder.release();
            mRecorder = null;
            return;
        }
        // 开始录制
        mRecorder.start();
        ivStartStop.setVisibility(View.VISIBLE);
    }


    private Handler mHandler;

    /**
     * 开始更新录制进度
     */
    private void startRecordProgress() {
        recordProgressTextTask = new UpdateRecordProgressTextTask();
        mStartTime = System.currentTimeMillis();
        mHandler.postDelayed(recordProgressTextTask, 200);
    }

    private void stopProgress() {
        mHandler.removeCallbacks(recordProgressTextTask);
    }

    private UpdateRecordProgressTextTask recordProgressTextTask;


    private class UpdateRecordProgressTextTask implements Runnable {

        @Override
        public void run() {
            int second = (int) ((System.currentTimeMillis() - mStartTime) / 1000);
            int recordSecond = startRecordSecond + second;
            currentRecordSecond = recordSecond;
            setProgressText(recordSecond);
            Log.e("lyq", second + "");
            mHandler.postDelayed(recordProgressTextTask, 200);
        }
    }

    private void changeVideo() {
        cameraCount = Camera.getNumberOfCameras();
        if (isCameraBack) {
            isCameraBack = false;
        } else {
            isCameraBack = true;
        }
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int i = 0; i < cameraCount; i++) {

            Camera.getCameraInfo(i, cameraInfo);//得到每一个摄像头的信息
            if (cameraPosition == 1) {
                //现在是后置，变更为前置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {//代表摄像头的方位，CAMERA_FACING_FRONT前置      CAMERA_FACING_BACK后置

                    mCamera.stopPreview();//停掉原来摄像头的预览
                    mCamera.release();//释放资源
                    mCamera = null;//取消原来摄像头
                    mCamera = Camera.open(i);//打开当前选中的摄像头
                    cameraPosition = 0;
                    try {
                        mCamera = deal(mCamera);
                        mCamera.setPreviewDisplay(mSurfaceHolder);//通过surfaceview显示取景画面
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mCamera.startPreview();//开始预览
//                    mCamera.setDisplayOrientation(90); // 2 3代
//                    mCamera.setDisplayOrientation(270); // 4代
                    break;
                }
            } else {
                //现在是前置， 变更为后置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {// 代表摄像头的方位，CAMERA_FACING_FRONT前置
                    // CAMERA_FACING_BACK后置

                    mCamera.stopPreview();// 停掉原来摄像头的预览
                    mCamera.release();// 停掉原来摄像头的预览
                    mCamera = null;// 释放资源
                    mCamera = Camera.open(i);// 取消原来摄像头
                    cameraPosition = 1;
                    try {
                        mCamera = deal(mCamera);
                        mCamera.setPreviewDisplay(mSurfaceHolder);// 通过surfaceview显示取景画面
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mCamera.startPreview();// 开始预览
                    break;
                }
            }

        }
    }

    /**
     * 设置camera基础属性及最佳预览比例
     *
     * @param camera Camera
     * @return Camera
     */
    public Camera deal(Camera camera) {
        // 设置camera预览的角度，因为默认图片是倾斜90度的
        if (cameraPosition == 1) {
            camera.setDisplayOrientation(90);
        } else {
            camera.setDisplayOrientation(270);
        }

        Size pictureSize = null;
        Size previewSize = null;
        Camera.Parameters parameters = camera.getParameters();
        parameters.set("orientation", "portrait"); // 设置竖屏
        //设置聚焦模式
        List<String> modes = parameters.getSupportedFocusModes();
        if (modes.contains("continuous-video")) // 如果硬件支持持续自动对焦就用，没有就用默认的模式
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        //缩短Recording启动时间
        parameters.setRecordingHint(true);
        //是否支持影像稳定能力，支持则开启
        if (parameters.isVideoStabilizationSupported())
            parameters.setVideoStabilization(true);
//        // 设置旋转代码
//        parameters.setRotation(90);
        // 获取硬件支持的录制像素列表
        List<Size> supportedPictureSizes = SupportedSizesReflect.getSupportedPictureSizes(parameters);
        // 获取硬件支持的预览像素列表
        List<Size> supportedPreviewSizes = SupportedSizesReflect.getSupportedPreviewSizes(parameters);
        for (Size size : supportedPictureSizes) {
            float width = size.width;
            float proportion = width / size.height;
            Log.e("lwh", "\nPicture width: " + size.width + "   &&&&& height: " + size.height);
            Log.e("lwh", "\nPicture proportion: " + proportion);
        }
        for (Size size : supportedPreviewSizes) {
            float width = size.width;
            float proportion = width / size.height;
            Log.e("lwh", "\nPreview width: " + size.width + "   &&&&& height: " + size.height);
            Log.e("lwh", "\nPreview proportion: " + proportion);
        }

        if (supportedPictureSizes != null && supportedPreviewSizes != null &&
                supportedPictureSizes.size() > 0 && supportedPreviewSizes.size() > 0) {

            pictureSize = supportedPictureSizes.get(0);
            int maxSize = 1920;
            if (maxSize > 0) {
                for (Size size : supportedPictureSizes) {
                    if (maxSize >= Math.max(size.width, size.height)) {
                        pictureSize = size;
                        break;
                    }
                }
            }

            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);
            float width = display.getWidth();
            float proportion = display.getHeight() / width;
            Log.e("lwh", "\nDisplay: width" + display.getWidth() + "    &&&&& height: " + display.getHeight());
            Log.e("lwh", "\nDisplay: proportion" + proportion);
            previewSize = getOptimalPreviewSize(supportedPreviewSizes, display.getWidth(), display.getHeight());

            parameters.setPreviewSize(previewSize.width, previewSize.height);
            parameters.setPictureSize(pictureSize.width, pictureSize.height);
            Log.e("lwh", "\nBest Picture width: " + pictureSize.width + "   &&&&& height: " + pictureSize.height);
            Log.e("lwh", "\nBest Preview width: " + previewSize.width + "   &&&&& height: " + previewSize.height);
            Log.e("lwh", "\n--------------------------------------------------------------->");
        }
        camera.cancelAutoFocus();
        camera.setParameters(parameters);
        return camera;
    }

    /**
     * 获取最匹配surfaceView像素尺寸的硬件支持的预览像素尺寸
     *
     * @param sizes 硬件支持的像素尺寸列表
     * @param w     surfaceView的宽
     * @param h     surfaceView的高
     * @return Camera.Size
     */
    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final int HEIGHT_WIDTH_DIFF = 500;
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w; //因为设置了竖屏所以宽高对调一下（android系统相机默认是竖屏的）
        if (sizes == null)
            return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = w;

        // 根据屏宽比和像素大小一起比较取出最优支持的屏宽size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
                if (minDiff > HEIGHT_WIDTH_DIFF) {
                    //如果比例是比较符合但像素差太远那就摒弃用这个
                    optimalSize = null;
                }
            }
        }

        // 如果没有比较符合屏宽比的size那么就取像素最接近的一个所支持的size
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    private String formatTime(int second) {
        int sec = 0;
        int min = 0;
        int hour = 0;
        if (second < 60) {
            sec = second;
            min = 0;
            hour = 0;
            return numFormat(sec) + "″";
        }
        if (second >= 60 && second < 3600) {
            min = second / 60;
            sec = second - min * 60;
            return numFormat(min) + ":" + numFormat(sec) + "″";
        }
        if (second >= 3600) {
            hour = second / 3600;
            min = (second - hour * 3600) / 60;
            sec = second - (hour * 3600 + min * 60);
            return numFormat(hour) + ":" + numFormat(min) + ":" + numFormat(sec) + "″";
        }
        return "00";
    }

    private String numFormat(int num) {
        StringBuffer sb = new StringBuffer("0");
        if (num < 10) {
            return sb.append(num).toString();
        } else {
            return String.valueOf(num);
        }
    }

    /**
     * 合并录像视频方法
     */
    private void mergeRecordVideoFile() {
        asyncTask = new MergeFileAsyncTask() {
            @Override
            void onComplete() {
                super.onComplete();
                if (asyncTask != null && !asyncTask.isCancelled()) {
                    asyncTask.cancel(true);
                    asyncTask = null;
                }
            }
        };
        asyncTask.execute(saveVideoPath, currentVideoFilePath);
    }

    /**
     * 创建视频文件保存路径
     */
    public static String getSDPath() {

        if (!Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            return null;
        }

        File sdDir = Environment.getExternalStorageDirectory();
        File eis = new File(sdDir.toString() + "/RecordVideo/");
        if (!eis.exists()) {
            eis.mkdir();
        }

        return sdDir.toString() + "/RecordVideo/";
    }

    private String getVideoName() {
        return "VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";
    }

    public abstract static class MergeFileAsyncTask extends AsyncTask<String, Integer, Object> {

        @Override
        protected Object doInBackground(String... strings) {
            try {
                String[] str = new String[]{strings[0], strings[1]};
                Log.e("path", "save:   " + strings[0] + "\ncurrent:   " + strings[1]);
                //将2个视频文件合并到 append.mp4文件下
                VideoUtils.appendVideo(getSDPath() + "append.mp4", str);
                File reName = new File(strings[0]);
                File f = new File(getSDPath() + "append.mp4");
                //再将合成的append.mp4视频文件 移动到 saveVideoPath 路径下
                f.renameTo(reName);
                if (reName.exists()) {
                    f.delete();
                    new File(strings[1]).delete();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            Log.e("lvweihao", values + "");
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            onComplete();
            Log.e("lvweihao", "onComplete");
        }

        void onComplete() {
        }
    }
}
