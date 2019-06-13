package com.hechuangwu.camera2record;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.hechuangwu.utils.Camera2Config;
import com.hechuangwu.utils.Camera2Utils;
import com.hechuangwu.widget.ProgressView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class VideoActivity extends AppCompatActivity implements View.OnClickListener, TextureView.SurfaceTextureListener {
    private TextureView mTextureView;
    private TextView tvBalanceTime;//录制剩余时间
    private ImageView ivTakePhoto;//拍照&录像按钮
    private ImageView ivSwitchCamera;//切换前后摄像头
    private ImageView ivLightOn;//开关闪光灯
    private ImageView ivClose;//关闭该Activity
    private ProgressView mProgressView;//录像进度条

    private Handler mCameraHandler;
    private HandlerThread mCameraThread;
    private boolean isRecording = false;//是否正在录制视频
    private RecordPressRunnable mRecordPressRunnable;//录制线程


    private int mWidth;//TextureView宽
    private int mHeight;//TextureView高
    private String mCameraIdBack;
    private String mCameraIdFont;
    private boolean isCameraFont;
    private CameraCharacteristics mCameraCharacteristics;
    private Integer mSensorOrientation;
    private Size mPreviewSize;
    private Size mCaptureSize;

    private static final int CAPTURE_OK = 0;//拍照完成回调
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private ImageReader mImageReader;
    private boolean isLightOn;
    private float finger_spacing;
    private int zoom_level = 0;
    private Rect zoom;
    private CaptureRequest.Builder mCaptureBuilder;
    private CaptureRequest mCaptureRequest;
    private MediaRecorder mMediaRecorder;
    private String mVideoPath;
    private boolean isStop ;//是否停止录制
    private int mCurrentTime;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        //全屏模式
        requestWindowFeature( Window.FEATURE_NO_TITLE );
        getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );
        setContentView( R.layout.activity_video );

        initViews();
        initEvent();
    }

    //初始化视图控件
    private void initViews() {
        mTextureView = findViewById( R.id.video_texture );
        tvBalanceTime = findViewById( R.id.tv_balanceTime );
        ivTakePhoto = findViewById( R.id.iv_takePhoto );
        ivSwitchCamera = findViewById( R.id.iv_switchCamera );
        ivLightOn = findViewById( R.id.iv_lightOn );
        ivClose = findViewById( R.id.iv_close );
        mProgressView = findViewById( R.id.progressView );
    }

    private void initEvent() {
        ivSwitchCamera.setOnClickListener( this );
        ivLightOn.setOnClickListener( this );
        ivClose.setOnClickListener( this );

        mCameraThread = new HandlerThread( "CameraThread" );
        mCameraThread.start();
        mCameraHandler = new Handler( mCameraThread.getLooper() );

        mTextureView.setSurfaceTextureListener( this );

        //屏幕触摸事件
        onTouchListener();
    }

    private void onTouchListener() {
        mRecordPressRunnable = new RecordPressRunnable();
        ivTakePhoto.setOnTouchListener( new GestureListener() );
        mTextureView.setOnTouchListener( new ZoomListener() );
    }


    //缩放镜头
    class ZoomListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            try {
                //活动区域宽度和作物区域宽度之比和活动区域高度和作物区域高度之比的最大比率
                float maxZoom = (mCameraCharacteristics.get( CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM )) * 10;
                Rect m = mCameraCharacteristics.get( CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE );

                int action = event.getAction();
                float current_finger_spacing;
                //判断当前屏幕的手指数
                if (event.getPointerCount() > 1) {

                    //计算两个触摸点的距离
                    float x = event.getX( 0 ) - event.getX( 1 );
                    float y = event.getY( 0 ) - event.getY( 1 );
                    current_finger_spacing = (float) Math.sqrt( x * x + y * y );

                    if (finger_spacing != 0) {
                        if (current_finger_spacing > finger_spacing && maxZoom > zoom_level) {
                            zoom_level++;

                        } else if (current_finger_spacing < finger_spacing && zoom_level > 1) {
                            zoom_level--;
                        }

                        int minW = (int) (m.width() / maxZoom);
                        int minH = (int) (m.height() / maxZoom);
                        int difW = m.width() - minW;
                        int difH = m.height() - minH;
                        int cropW = difW / 100 * (int) zoom_level;
                        int cropH = difH / 100 * (int) zoom_level;
                        cropW -= cropW & 3;
                        cropH -= cropH & 3;
                        zoom = new Rect( cropW, cropH, m.width() - cropW, m.height() - cropH );
                        mCaptureBuilder.set( CaptureRequest.SCALER_CROP_REGION, zoom );
                    }
                    finger_spacing = current_finger_spacing;
                } else {
                    if (action == MotionEvent.ACTION_UP) {
                        //single touch logic,可做点击聚焦操作
                    }
                }

                try {
                    mPreviewSession.setRepeatingRequest( mCaptureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                                @Override
                                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                    super.onCaptureCompleted( session, request, result );
                                }
                            },
                            null );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                throw new RuntimeException( "can not access camera.", e );
            }

            return true;
        }
    }


    //录制或者拍照触摸事件
    class GestureListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isRecording = false;
                    //长按超过500毫秒为录像
                    mCameraHandler.postDelayed( mRecordPressRunnable, 500 );
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handleRecordOrCapture();
                    break;
            }

            return true;
        }
    }

    private void handleRecordOrCapture() {
        mCameraHandler.removeCallbacks( mRecordPressRunnable );
        //暂停录制
        if (isRecording) {
            isRecording = false;
            mCameraHandler.postDelayed( new Runnable() {
                @Override
                public void run() {
                    if(!isStop){
                        stopRecord();
                    }
                }
            } ,200);

        }
        //拍照
        else {
            isRecording = false;
            if (Camera2Config.ENABLE_CAPTURE) {
                capture();
            }
        }
    }

    //拍照
    private void capture() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest( CameraDevice.TEMPLATE_STILL_CAPTURE );
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.addTarget( mImageReader.getSurface() );
            //前置需要做旋转
            if (isCameraFont) {
                captureBuilder.set( CaptureRequest.JPEG_ORIENTATION, Camera2Config.ORIENTATION.get( Surface.ROTATION_180 ) );
            } else {
                captureBuilder.set( CaptureRequest.JPEG_ORIENTATION, Camera2Config.ORIENTATION.get( rotation ) );
            }
            //锁住焦点
            captureBuilder.set( CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START );
            //闪光灯设置
            if (isLightOn) {
                captureBuilder.set( CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH );
            } else {
                captureBuilder.set( CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF );
            }

            captureBuilder.set( CaptureRequest.SCALER_CROP_REGION, zoom );

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    //拍完照unLockFocus
                    unLockFocus();
                }
            };
            //停止预览请求
            mPreviewSession.stopRepeating();
            //拍照
            mPreviewSession.capture( captureBuilder.build(), captureCallback, null );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //录像
    private class RecordPressRunnable implements Runnable {

        @Override
        public void run() {
            if (Camera2Config.ENABLE_RECORD) {
                startRecordPreview();//录像预览和初始化
                startRecord();//开始录制
                isRecording = true;
            }
        }
    }

    //Texture初始化成功自动回调
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
        setupCamera();
        openCamera( mCameraIdBack );

    }

    //打开相机
    private void openCamera(String cameraId) {
        CameraManager systemService = (CameraManager) getSystemService( Context.CAMERA_SERVICE );
        try {
            if (ActivityCompat.checkSelfPermission( this, Manifest.permission.CAMERA ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            systemService.openCamera( cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    mCameraDevice = camera;
                    //设备成功开启，再开启预览
                    startPreview();
                    if (null != mTextureView) {
                        configureTransform( mTextureView.getWidth(), mTextureView.getHeight() );
                    }
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    cameraDevice.close();
                    mCameraDevice = null;

                }

                @Override
                public void onError(CameraDevice cameraDevice, int error) {
                    cameraDevice.close();
                    mCameraDevice = null;
                }
            }, null );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //录制或拍照前的预览
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }

        try {

            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            if (surfaceTexture == null) {
                return;
            }
            //关闭其他存在的回话
            closePreviewSession();
            //纹理缓存大小
            surfaceTexture.setDefaultBufferSize( mPreviewSize.getWidth(), mPreviewSize.getHeight() );
            //设备builder
            mCaptureBuilder = mCameraDevice.createCaptureRequest( CameraDevice.TEMPLATE_PREVIEW );
            //设备设置预览数据
            Surface surface = new Surface( surfaceTexture );
            mCaptureBuilder.addTarget( surface );
            //默认关闭闪光灯
            mCaptureBuilder.set( CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF );
            //创建设备会话
            mCameraDevice.createCaptureSession( Arrays.asList( surface, mImageReader.getSurface() ), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        mCaptureRequest = mCaptureBuilder.build();
                        mPreviewSession = session;
                        mPreviewSession.setRepeatingRequest( mCaptureRequest, null, mCameraHandler );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, null );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    //初始化相机参数
    private void setupCamera() {
        CameraManager cameraManager = (CameraManager) this.getSystemService( Context.CAMERA_SERVICE );
        try {
            //0后置摄像，1前置摄像
            String[] cameraIdList = cameraManager.getCameraIdList();
            mCameraIdBack = cameraIdList[0];
            mCameraIdFont = cameraIdList[1];

            if (isCameraFont) {
                mCameraCharacteristics = cameraManager.getCameraCharacteristics( mCameraIdFont );
            } else {
                mCameraCharacteristics = cameraManager.getCameraCharacteristics( mCameraIdBack );
            }

            //管理摄像头支持的所有输出格式和尺寸
            StreamConfigurationMap streamConfigurationMap = mCameraCharacteristics.get( CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP );
            //竖屏方向
            mSensorOrientation = mCameraCharacteristics.get( CameraCharacteristics.SENSOR_ORIENTATION );
            //获取预览最小尺寸
            mPreviewSize = Camera2Utils.getMinPreSize( streamConfigurationMap.getOutputSizes( SurfaceTexture.class ), mWidth, mHeight, Camera2Config.PREVIEW_MAX_HEIGHT );
            //相机支持的最大尺寸
            mCaptureSize = Collections.max( Arrays.asList( streamConfigurationMap.getOutputSizes( ImageFormat.JPEG ) ), new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum( lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth() );
                }
            } );
            //手机旋转
            configureTransform( mWidth, mHeight );

            //拍照初始化
            setupImageReader();

            //录制初始化
            mMediaRecorder = new MediaRecorder();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //拍照初始化
    private void setupImageReader() {
        //最多获取两帧图像流
        mImageReader = ImageReader.newInstance( mCaptureSize.getWidth(), mCaptureSize.getHeight(), ImageFormat.JPEG, 2 );
        mImageReader.setOnImageAvailableListener( new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                //抓取的数据存放到数组中
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] data = new byte[buffer.remaining()];
                buffer.get( data );

                //存储格式和路径
                String timeStamp = new SimpleDateFormat( "yyyyMMdd_HHmmss" ).format( new Date() );
                String folder = Camera2Utils.getCamera2DefaultPath( null );
                String picSavePath = folder + "IMG_" + timeStamp + ".jpg";
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream( picSavePath );
                    fos.write( data, 0, data.length );

                    Message msg = new Message();
                    msg.what = CAPTURE_OK;
                    msg.obj = picSavePath;
                    mCameraHandler.sendMessage( msg );
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                image.close();
            }
        }, mCameraHandler );

        mCameraHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage( msg );
                switch (msg.what) {
                    //拍照完成
                    case CAPTURE_OK:
                        String picSavePath = (String) msg.obj;
                        Intent intent = new Intent( VideoActivity.this, PreviewActivity.class );
                        intent.putExtra( Camera2Config.INTENT_PATH_SAVE_PIC, picSavePath );
                        startActivity( intent );
                        break;
                }
            }

        };
    }

    //录像预览
    private void startRecordPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        closePreviewSession();
        //录像初始化
        setupRecorder();

        try {
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            assert surfaceTexture != null;
            surfaceTexture.setDefaultBufferSize( mPreviewSize.getWidth(), mPreviewSize.getHeight() );
            mCaptureBuilder = mCameraDevice.createCaptureRequest( CameraDevice.TEMPLATE_RECORD );

            //数据采集到surfaces中
            List<Surface> surfaces = new ArrayList<>();
            Surface previewSurface = new Surface( surfaceTexture );
            surfaces.add( previewSurface );
            mCaptureBuilder.addTarget( previewSurface );

            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add( recorderSurface );
            mCaptureBuilder.addTarget( recorderSurface );

            //闪光灯
            if (isLightOn) {
                mCaptureBuilder.set( CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH );
            } else {
                mCaptureBuilder.set( CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF );
            }

            //保持缩放比例
            mCaptureBuilder.set( CaptureRequest.SCALER_CROP_REGION, zoom );

            mCameraDevice.createCaptureSession( surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        mCaptureRequest = mCaptureBuilder.build();
                        mPreviewSession = session;
                        mPreviewSession.setRepeatingRequest( mCaptureRequest,null,mCameraHandler );
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Toast.makeText(VideoActivity.this, "onConfigureFailed", Toast.LENGTH_SHORT).show();
                }


            }, null );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //录像初始化
    private void setupRecorder() {
        try {
            mMediaRecorder.reset();
            mMediaRecorder.setAudioSource( MediaRecorder.AudioSource.MIC );
            mMediaRecorder.setVideoSource( MediaRecorder.VideoSource.SURFACE );

            if (CamcorderProfile.hasProfile( CamcorderProfile.QUALITY_480P )) {
                CamcorderProfile camcorderProfile = CamcorderProfile.get( CamcorderProfile.QUALITY_480P );
                camcorderProfile.videoBitRate = mPreviewSize.getWidth() * mPreviewSize.getHeight();
                mMediaRecorder.setProfile( camcorderProfile );
                mMediaRecorder.setPreviewDisplay( new Surface( mTextureView.getSurfaceTexture() ) );
            } else if (CamcorderProfile.hasProfile( CamcorderProfile.QUALITY_720P )) {
                CamcorderProfile profile = CamcorderProfile.get( CamcorderProfile.QUALITY_720P );
                profile.videoBitRate = mPreviewSize.getWidth() * mPreviewSize.getHeight();
                mMediaRecorder.setProfile( profile );
                mMediaRecorder.setPreviewDisplay( new Surface( mTextureView.getSurfaceTexture() ) );
            } else if (CamcorderProfile.hasProfile( CamcorderProfile.QUALITY_QVGA )) {
                mMediaRecorder.setProfile( CamcorderProfile.get( CamcorderProfile.QUALITY_QVGA ) );
                mMediaRecorder.setPreviewDisplay( new Surface( mTextureView.getSurfaceTexture() ) );
            } else if (CamcorderProfile.hasProfile( CamcorderProfile.QUALITY_CIF )) {
                mMediaRecorder.setProfile( CamcorderProfile.get( CamcorderProfile.QUALITY_CIF ) );
                mMediaRecorder.setPreviewDisplay( new Surface( mTextureView.getSurfaceTexture() ) );
            } else {
                mMediaRecorder.setOutputFormat( MediaRecorder.OutputFormat.MPEG_4 );
                mMediaRecorder.setVideoEncoder( MediaRecorder.VideoEncoder.H264 );
                mMediaRecorder.setAudioEncoder( MediaRecorder.AudioEncoder.AAC );
                mMediaRecorder.setVideoEncodingBitRate(2500000);
                mMediaRecorder.setVideoFrameRate(30);
                mMediaRecorder.setVideoSize( mPreviewSize.getWidth(), mPreviewSize.getHeight() );
            }

            //设置输出路径
            String timeStamp = new SimpleDateFormat( "yyyyMMdd_HHmmss" ).format( new Date() );
            String folder = Camera2Utils.getCamera2DefaultPath( null );
            mVideoPath = folder + "VIDEO_" + timeStamp + ".mp4";
            mMediaRecorder.setOutputFile( mVideoPath );

            //如果是前置需要旋转
            if (isCameraFont) {
                mMediaRecorder.setOrientationHint( 270 );
            } else {
                mMediaRecorder.setOrientationHint( 90 );
            }
            //开始录制
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //开始录制
    private void startRecord(){
        mMediaRecorder.start();
        isStop = false;
        mCameraHandler.postDelayed( mRecordRunnable ,0);
    }

    Runnable mRecordRunnable = new Runnable() {
        @Override
        public void run() {
            mCurrentTime++;
            mProgressView.setVisibility(View.VISIBLE);
            mProgressView.setIsStart(true);
            //显示时间
            tvBalanceTime.setVisibility(View.VISIBLE);
            tvBalanceTime.setText(Camera2Config.RECORD_MAX_TIME - mCurrentTime + "s");

            //超过最大时间自动结束
            if (mCurrentTime > Camera2Config.RECORD_MAX_TIME) {
                stopRecord();
            } else {
                mCameraHandler.postDelayed(this, 1000);
            }
        }
    };

    //停止录制
    private void stopRecord(){
        if (TextUtils.isEmpty(mVideoPath)) {
            return;
        }
        mProgressView.setIsStart( false );
        mCameraHandler.removeCallbacks( mRecordRunnable );
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        isStop = true;
        if(mCurrentTime<=Camera2Config.RECORD_MIN_TIME){
            Toast.makeText(this, "录制时间过短", Toast.LENGTH_LONG).show();
        }else {
            //跳转播放视频
            Intent intent = new Intent( this, PreviewActivity.class );
            intent.putExtra( Camera2Config.INTENT_PATH_SAVE_VIDEO,mVideoPath );
            startActivity( intent );
        }
        resetCamera();
        mCurrentTime = 0;
    }


    //重置相机
    private void resetCamera(){
        mProgressView.setVisibility(View.INVISIBLE);
        tvBalanceTime.setVisibility(View.GONE);
        mProgressView.reset();

        if (TextUtils.isEmpty(mCameraIdBack)) {
            return;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
        setupCamera();
        openCamera(mCameraIdBack);
    }

    //手机旋转
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF( 0, 0, viewWidth, viewHeight );
        RectF bufferRect = new RectF( 0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth() );
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset( centerX - bufferRect.centerX(), centerY - bufferRect.centerY() );
            matrix.setRectToRect( viewRect, bufferRect, Matrix.ScaleToFit.FILL );
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth() );
            matrix.postScale( scale, scale, centerX, centerY );
            matrix.postRotate( 90 * (rotation - 2), centerX, centerY );
        }
        mTextureView.setTransform( matrix );
    }
    //清除预览Session
    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    //拍完照释放焦点
    private void unLockFocus() {
        try {
            // 构建失能AF的请求
            mCaptureBuilder.set( CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL );
            //闪光灯重置为未开启状态
            mCaptureBuilder.set( CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF );
            //继续开启预览
            mPreviewSession.setRepeatingRequest( mCaptureRequest, null, mCameraHandler );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //释放资源
    private void freeSource() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }

        if (mCameraHandler != null) {
            mCameraHandler.removeCallbacksAndMessages( null );
        }

    }

    //前后置切换
    public void switchCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (isCameraFont) {
            isCameraFont = false;
            setupCamera();
            openCamera(mCameraIdBack);
        } else {
            isCameraFont = true;
            setupCamera();
            openCamera(mCameraIdFont);
        }
    }

    //闪光灯
    public void openLight() {
        if (isLightOn) {
            ivLightOn.setSelected(false);
            isLightOn = false;
            mCaptureBuilder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_OFF);
        } else {
            ivLightOn.setSelected(true);
            isLightOn = true;
            mCaptureBuilder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_TORCH);
        }

        try {
            if (mPreviewSession != null)
                mPreviewSession.setRepeatingRequest(mCaptureBuilder.build(), null, mCameraHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            //切换镜头
            case R.id.iv_switchCamera:
                switchCamera();
                break;
            //闪光灯
            case R.id.iv_lightOn:
                openLight();
                break;
            //关闭
            case R.id.iv_close:
                finish();
                break;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        //从FinishActivity退回来的时候默认重置为初始状态，因为有些机型从不可见到可见不会执行onSurfaceTextureAvailable，像有些一加手机
        //所以也可以在这里在进行setupCamera()和openCamera()这两个方法
        //每次开启预览缩放重置为正常状态
        if (zoom != null) {
            zoom.setEmpty();
            zoom_level = 0;
        }

        //每次开启预览默认闪光灯没开启
        isLightOn = false;
        ivLightOn.setSelected( false );

        //每次开启预览默认是后置摄像头
        isCameraFont = false;
    }

    @Override
    protected void onStop() {
        freeSource();
        super.onStop();
    }


    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public synchronized ComponentName startForegroundServiceAsUser(Intent service, UserHandle user) {
        return null;
    }

}

