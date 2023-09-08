package com.vslam.orbslam3.vslamactivity;

/**
 * @Author : liuhao02
 * @Time : On 2023/7/10 17:34
 * @Description : VslamActivity2
 */

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arashivision.sdkcamera.camera.InstaCameraManager;
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener;
import com.arashivision.sdkcamera.camera.preview.GyroData;
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder;
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView;
import com.arashivision.sdkmedia.player.listener.PlayerViewListener;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



import com.arashivision.sdkcamera.camera.callback.ICameraChangedCallback;

/**
 * 基于影石数据
 */
public class VslamActivity123 extends  AppCompatActivity implements SensorEventListener, IPreviewStatusListener,ICameraChangedCallback {

    private static final String TAG = "VslamActivity";
    private GLSurfaceView glSurfaceView;
    private CameraBridgeViewBase mOpenCvCameraView;
    private SeekBar seek;
    private TextView myTextView;
    public static double SCALE = 1;
    private static long count = 0;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private InstaCapturePlayerView mCapturePlayerView;
    private ToggleButton mBtnSwitch;

    private ImageReader mImageReader;
    private HandlerThread mImageReaderHandlerThread;
    private Handler mImageReaderHandler;

    private List<GyroData>  gyroData= new ArrayList<>();

    //    private float[] magValues = new float[3];
    private float[] orientationValues = new float[3];
    private float[] rotationMatrix = new float[9];

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        //显示窗口初始化设置
        MatrixState.set_projection_matrix(445f, 445f, 319.5f, 239.500000f, 850, 480, 0.01f, 100f);
        super.onCreate(savedInstanceState);
        //hide the status bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //hide the title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //读取布局xml设置
        setContentView(R.layout.activity_vslam_activity);

//        //预览SurfaceView初始化
//        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.mOpenCvCameraView);
//        mOpenCvCameraView.setMaxFrameSize(640, 480);
//        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        // mOpenCvCameraView.setCvCameraViewListener(this);

        //文本框初始化
        myTextView = (TextView) findViewById(R.id.myTextView);
        myTextView.setText("当前值Scale为:" + SCALE);

        //SeekBar初始化
        seek = (SeekBar) findViewById(R.id.mySeekBar);
        seek.setProgress(60);
        seek.setOnSeekBarChangeListener(seekListener);

        //OpenGL图层初始化和监听
        //opengl图层
        glSurfaceView = (GLSurfaceView) findViewById(R.id.glSurfaceView);
        //OpenGL ES 2.0
        glSurfaceView.setEGLContextClientVersion(2);
        //设置透明背景
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        final MyRender earthRender = new MyRender(this);
        glSurfaceView.setRenderer(earthRender);
        // 设置渲染模式为主动渲染
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glSurfaceView.setZOrderOnTop(true);
        glSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event != null) {
                    // Convert touch coordinates into normalized device
                    // coordinates, keeping in mind that Android's Y
                    // coordinates are inverted.
                    final float normalizedX = ((event.getX() / (float) v.getWidth()) * 2 - 1) * 4f;
                    final float normalizedY = (-((event.getY() / (float) v.getHeight()) * 2 - 1)) * 1.5f;

                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        glSurfaceView.queueEvent(new Runnable() {
                            @Override
                            public void run() {
                                earthRender.handleTouchPress(
                                        normalizedX, normalizedY);
                            }
                        });
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        glSurfaceView.queueEvent(new Runnable() {
                            @Override
                            public void run() {
                                earthRender.handleTouchDrag(
                                        normalizedX, normalizedY);
                            }
                        });
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        glSurfaceView.queueEvent(new Runnable() {
                            @Override
                            public void run() {
                                earthRender.handleTouchUp(
                                        normalizedX, normalizedY);
                            }
                        });
                    }

                    return true;
                } else {
                    return false;
                }
            }
        });

        //检查配置文件路径是否有读取权限
        boolean havePermission = getPermissionCamera(this);
        Log.i(TAG, "getPermissionCamera " + havePermission);

        //读取PARAconfig.yaml配置文件
        //String filepath = "/sdcard/Download/SLAM/Calibration/PARAconfig.yaml";
        String filepath = "/storage/emulated/0/SLAM/Calibration/PARAconfig.yaml";
        //String filepath  = getExternalFilesDir("SLAM").getPath() + "/Calibration/PARAconfig.yaml";
        System.out.println("PARAconfig.yaml filepath=" + filepath);
        try {
            readFileOnLine(filepath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        CameraBindNetworkManager.getInstance().bindNetwork(errorCode -> {
            Log.d(TAG, "bindNetwork(: errorCode  "+errorCode);
            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI);
            InstaCameraManager.getInstance().registerCameraChangedCallback(this);
            mCapturePlayerView = findViewById(R.id.player_capture);
            mCapturePlayerView.setLifecycle(getLifecycle());
            // mCapturePlayerView.setLifecycle(getLifecycle());
            mCapturePlayerView.post(() -> {
                Log.d(TAG, "onCreate: mCapturePlayerView.post(()");

                /// createSurfaceView();
            });
        });


    }

    @Override
    public void onCameraStatusChanged(boolean enabled) {
        Log.d(TAG, "onCameraStatus"+enabled);
        InstaCameraManager.getInstance().setPreviewStatusChangedListener(this);
        InstaCameraManager.getInstance().startPreviewStream();
        ICameraChangedCallback.super.onCameraStatusChanged(enabled);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: ");
        super.onStop();
        if (isFinishing()) {
            // Auto close preview after page loses focus
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(null);
            InstaCameraManager.getInstance().closePreviewStream();
            mCapturePlayerView.destroy();
        }
    }

    @Override
    public void onOpening() {
        Log.d(TAG, "onOpening: ");
        // Preview Opening
        // mBtnSwitch.setChecked(true);
        // If you want to set your custom surface, do like this.
        createSurfaceView();
    }
    @Override
    public void onOpened() {
        Log.d(TAG, "onOpened: ");
        // Preview stream is on and can be played
        InstaCameraManager.getInstance().setStreamEncode();
        mCapturePlayerView.setPlayerViewListener(new PlayerViewListener() {
            @Override
            public void onLoadingFinish() {
                InstaCameraManager.getInstance().setPipeline(mCapturePlayerView.getPipeline());
            }

            @Override
            public void onReleaseCameraPipeline() {
                InstaCameraManager.getInstance().setPipeline(null);
            }
        });
        mCapturePlayerView.prepare(createParams());
        mCapturePlayerView.play();
        mCapturePlayerView.setKeepScreenOn(true);
    }
    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        glSurfaceView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
//        if (mOpenCvCameraView != null)
//            mOpenCvCameraView.disableView();

        glSurfaceView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        InstaCameraManager.getInstance().unregisterCameraChangedCallback(this);
//        if (mOpenCvCameraView != null)
//            mOpenCvCameraView.disableView();

    }


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }


    private CaptureParamsBuilder createParams() {
        CaptureParamsBuilder builder = new CaptureParamsBuilder()
                .setCameraType(InstaCameraManager.getInstance().getCameraType())
                .setMediaOffset(InstaCameraManager.getInstance().getMediaOffset())
                .setMediaOffsetV2(InstaCameraManager.getInstance().getMediaOffsetV2())
                .setMediaOffsetV3(InstaCameraManager.getInstance().getMediaOffsetV3())
                .setCameraSelfie(InstaCameraManager.getInstance().isCameraSelfie())
                .setGyroTimeStamp(InstaCameraManager.getInstance().getGyroTimeStamp())
                .setBatteryType(InstaCameraManager.getInstance().getBatteryType())
                .setCameraRenderSurfaceInfo(mImageReader.getSurface(), mImageReader.getWidth(), mImageReader.getHeight());
        return builder;
    }

    //private native float[] CVTest(long matAddr, String timeStamp);  //调用 c++代码
    private native float[] CVTest(long matAddr, float[] acc_x, float[] acc_y, float[] acc_z, float[] ang_vel_x, float[] ang_vel_y, float[] ang_vel_z, double[] timestamp, int index);  //调用 c++代码


    private void createSurfaceView() {
        Log.d(TAG, "createSurfaceView: ");
        if (mImageReader != null) {
            Log.d(TAG, "createSurfaceView:mImageReader != null return ");
            return;
        }


        File dir = new File(getExternalCacheDir(), "preview_jpg");
        dir.mkdirs();
        mImageReaderHandlerThread = new HandlerThread("camera render surface");
        mImageReaderHandlerThread.start();

        mImageReaderHandler = new Handler(mImageReaderHandlerThread.getLooper());
        //todo
        mImageReader = ImageReader.newInstance(mCapturePlayerView.getWidth(), mCapturePlayerView.getHeight(), PixelFormat.RGBA_8888, 1);

        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireLatestImage();
                Log.i(TAG, "image format " + image.getFormat()
                        + " getWidth " + image.getWidth()
                        + " get height " + image.getHeight()
                        + " timestamp " + image.getTimestamp());
                int planeCount = image.getPlanes().length;


                Log.i(TAG, "plane count " + planeCount);
                Image.Plane plane = image.getPlanes()[0];
                int pixelStride = plane.getPixelStride();
                int rowStride = plane.getRowStride();
                int rowPadding = rowStride - pixelStride * image.getWidth();
                Log.i(TAG, " plane getPixelStride " + pixelStride + " getRowStride " + rowStride);

                Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(plane.getBuffer());

                String filePath = dir.getAbsolutePath() + "/" + image.getTimestamp() + ".png";
                File imageFile = new File(filePath);

                //cv2.imread
                //  FileOutputStream os = null;
                try {
                    // os = new FileOutputStream(imageFile);

                    //bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                    Mat mat = new Mat();
                    Utils.bitmapToMat(bitmap, mat);
                    Mat temp = onCameraFrame(mat);

                    Utils.matToBitmap(temp, bitmap);
                    ImageView imageView = findViewById(R.id.mOpenCvCameraView1);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(bitmap);
                        }
                    });
                    Log.i(TAG, "path " + filePath);
//                    try {
//                        os.flush();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                image.close();
            }
        }, mImageReaderHandler);
    }

    /**
     * 处理图像的函数，这个函数在相机刷新每一帧都会调用一次，而且每次的输入参数就是当前相机视图信息
     * * @param inputFrame
     *
     * @return
     */
    public Mat onCameraFrame(Mat rgb) {
        Log.d(TAG, "onCameraFrame: ");
        int index = gyroData.size();
        float[] acc_x = new float[3000];
        float[] acc_y = new float[3000];
        float[] acc_z = new float[3000];
        float[] ang_vel_x = new float[3000];
        float[] ang_vel_y = new float[3000];
        float[] ang_vel_z = new float[3000];
        double[] timestamp = new double[3000];
        for (int i = 0; i < index; i++) {
            acc_x[i] = (float) gyroData.get(i).ax;
            acc_y[i] = (float) gyroData.get(i).ay;
            acc_z[i] = (float) gyroData.get(i).az;
            ang_vel_x[i] = (float) gyroData.get(i).gx;
            ang_vel_y[i] = (float) gyroData.get(i).gy;
            ang_vel_z[i] = (float) gyroData.get(i).gz;
            timestamp[i] = (double) gyroData.get(i).timestamp;
        }
        float[] poseMatrix = CVTest(rgb.getNativeObjAddr(),acc_x,acc_y,acc_z,ang_vel_x,ang_vel_y,ang_vel_z,timestamp,index); //从slam系统获得相机位姿矩阵
        gyroData.clear();
        if (poseMatrix.length != 0) {
            double[][] pose = new double[4][4];
            for (int i = 0; i < poseMatrix.length / 4; i++) {
                for (int j = 0; j < 4; j++) {

                    if (j == 3 && i != 3) {
                        pose[i][j] = poseMatrix[i * 4 + j] * SCALE;
                    } else {
                        pose[i][j] = poseMatrix[i * 4 + j];
                    }
                    System.out.print(pose[i][j] + "\t ");
                }

                System.out.print("\n");
            }
            System.out.println("Total count =" + count + "frame,SCALE=============" + SCALE);
            double[][] R = new double[3][3];
            double[] T = new double[3];

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    R[i][j] = pose[i][j];
                }
            }
            for (int i = 0; i < 3; i++) {
                T[i] = pose[i][3];
            }
            RealMatrix rotation = new Array2DRowRealMatrix(R);
            RealMatrix translation = new Array2DRowRealMatrix(T);
            MatrixState.set_model_view_matrix(rotation, translation);
            printMatrix(rotation);
            printMatrix(translation);
            MyRender.flag = true;
            count++;

        } else {
            //如果没有得到相机的位姿矩阵，就不画地球/立方体
            MyRender.flag = false;
        }

//      CVTest(rgb.getNativeObjAddr());
        return rgb;
    }

    private Bitmap mCacheBitmap;

//    protected void deliverAndDrawFrame() {
//        Mat modified;
//
//      //  if (mListener != null) {
//       //     modified = onCameraFrame();
////        } else {
////            modified = frame.rgba();
////        }
//
//        boolean bmpValid = true;
//        if (modified != null) {
//            try {
//                Utils.matToBitmap(modified, mCacheBitmap);
//            } catch(Exception e) {
//                Log.e(TAG, "Mat type: " + modified);
//                Log.e(TAG, "Bitmap type: " + mCacheBitmap.getWidth() + "*" + mCacheBitmap.getHeight());
//                Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
//                bmpValid = false;
//            }
//        }
//
//        if (bmpValid && mCacheBitmap != null) {
//            Canvas canvas = getHolder().lockCanvas();
//            if (canvas != null) {
//                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
//                if (BuildConfig.DEBUG)
//                    Log.d(TAG, "mStretch value: " + mScale);
//
//                if (mScale != 0) {
//                    canvas.drawBitmap(mCacheBitmap, new Rect(0,0,mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
//                            new Rect((int)((canvas.getWidth() - mScale*mCacheBitmap.getWidth()) / 2),
//                                    (int)((canvas.getHeight() - mScale*mCacheBitmap.getHeight()) / 2),
//                                    (int)((canvas.getWidth() - mScale*mCacheBitmap.getWidth()) / 2 + mScale*mCacheBitmap.getWidth()),
//                                    (int)((canvas.getHeight() - mScale*mCacheBitmap.getHeight()) / 2 + mScale*mCacheBitmap.getHeight())), null);
//                } else {
//                    canvas.drawBitmap(mCacheBitmap, new Rect(0,0,mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
//                            new Rect((canvas.getWidth() - mCacheBitmap.getWidth()) / 2,
//                                    (canvas.getHeight() - mCacheBitmap.getHeight()) / 2,
//                                    (canvas.getWidth() - mCacheBitmap.getWidth()) / 2 + mCacheBitmap.getWidth(),
//                                    (canvas.getHeight() - mCacheBitmap.getHeight()) / 2 + mCacheBitmap.getHeight()), null);
//                }
//
//                if (mFpsMeter != null) {
//                    mFpsMeter.measure();
//                    mFpsMeter.draw(canvas, 20, 30);
//                }
//                getHolder().unlockCanvasAndPost(canvas);
//            }
//        }
//    }


    //@Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            System.out.println("CameraBridgeViewBase> cameraViews == null!!!");
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase : cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();
            }
        }
    }

    /**
     * 确认camera权限
     *
     * @param activity
     * @return
     */
    public boolean getPermissionCamera(Activity activity) {
        int cameraPermissionCheck = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);
        int readPermissionCheck = ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
        int writePermissionCheck = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (cameraPermissionCheck != PackageManager.PERMISSION_GRANTED
                || readPermissionCheck != PackageManager.PERMISSION_GRANTED
                || writePermissionCheck != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(
                    activity,
                    permissions,
                    0);
            return false;
        } else {
            onCameraPermissionGranted();
            return true;
        }
    }

    /**
     * 用于测试java读取文件权限的函数
     **/
    void readFileOnLine(String strFileName) throws Exception {

        int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        int permission = ActivityCompat.checkSelfPermission(VslamActivity123.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    VslamActivity123.this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
        FileInputStream fis = new FileInputStream(new File(strFileName));
        StringBuffer sBuffer = new StringBuffer();
        DataInputStream dataIO = new DataInputStream(fis);
        String strLine = null;
        while ((strLine = dataIO.readLine()) != null) {
            Log.i(TAG, strLine + "+++++++++++++++++++++++++++++++++++++++");
        }
        dataIO.close();
        fis.close();
    }

    /**
     * SeekBar监听器
     **/
    private SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Log.i(TAG, "onStopTrackingTouch");
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            Log.i(TAG, "onStartTrackingTouch");
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
            Log.i(TAG, "onProgressChanged");
            if (progress > 50) {
                SCALE = (progress - 50) * 10;
            } else {
                SCALE = (50 - progress) * 0.5;
            }
            myTextView.setText("当前值 为: " + SCALE);

        }
    };

    /**
     * OpenCV库链接加载管理回调函数
     **/
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    //  mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    /**
     * 打印Mat矩阵函数
     **/
    void printMatrix(RealMatrix input) {
        double matrixtoarray[][] = input.getData();
        for (int i = 0; i < matrixtoarray.length; i++) {
            for (int j = 0; j < matrixtoarray[0].length; j++) {
                System.out.print(matrixtoarray[i][j] + "\t");
            }
            System.out.print("\n");
        }
    }

    /**
     * Called when there is a new sensor event.  Note that "on changed"
     * is somewhat of a misnomer, as this will also be called if we have a
     * new reading from a sensor with the exact same sensor values (but a
     * newer timestamp).
     *
     * <p>See {@link SensorManager SensorManager}
     * for details on possible sensor types.
     * <p>See also {@link SensorEvent SensorEvent}.
     *
     * <p><b>NOTE:</b> The application doesn't own the
     * {@link SensorEvent event}
     * object passed as a parameter and therefore cannot hold on to it.
     * The object may be part of an internal pool and may be reused by
     * the framework.
     *
     * @param event the {@link SensorEvent SensorEvent}.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    /**
     * Called when the accuracy of the registered sensor has changed.  Unlike
     * onSensorChanged(), this is only called when this accuracy value changes.
     *
     * <p>See the SENSOR_STATUS_* constants in
     * {@link SensorManager SensorManager} for details.
     *
     * @param sensor
     * @param accuracy The new accuracy of this sensor, one of
     *                 {@code SensorManager.SENSOR_STATUS_*}
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onError() {
        Log.e(TAG, "onError: " );
        // IPreviewStatusListener.super.onError();
    }

    @Override
    public void onCameraConnectError(int errorCode) {
        Log.e(TAG, "onCameraConnectError: "+errorCode);
        // ICameraChangedCallback.super.onCameraConnectError(errorCode);
    }

    @Override
    public void onGyroData(List<GyroData> gyroList) {
        Log.d(TAG, "onGyroData: "+gyroList.size());
//        Log.d(TAG, "onGyroData: ax"+gyroList.get(0).ax);
//        Log.d(TAG, "onGyroData: ay"+gyroList.get(0).ay);
//        Log.d(TAG, "onGyroData: az"+gyroList.get(0).az);
//        Log.d(TAG, "onGyroData: gx"+gyroList.get(0).gx);
//        Log.d(TAG, "onGyroData: gy"+gyroList.get(0).gy);
//        Log.d(TAG, "onGyroData: gz"+gyroList.get(0).gz);
//        Log.d(TAG, "onGyroData: time"+gyroList.get(0).timestamp);
        gyroData.addAll(gyroList);
        // Callback frequency 10Hz, 50 data per group
        // gyroData.timestamp: The time since the camera was turned on
        // gyroData.ax, gyroData.ay, gyroData.az: Three-axis acceleration
        // gyroData.gx, gyroData.gy, gyroData.gz: Three-axis gyroscope
    }
}
