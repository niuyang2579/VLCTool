package com.niuyang.vlctool;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.support.v4.app.ActivityCompat;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.security.auth.callback.Callback;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final SparseIntArray ORIENTATION = new SparseIntArray();

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    //实验参数
    private String exposure_duration = "200";
    private String tag = "N5";
    private String server_url = "http://wsn.nwpu.info:3000/image/";
    private String capture_mode = "single";
    private String store_to_gallery = "1";

    private ImageView btnCapture;
    private ImageView btnSetting;
    private String mCameraId;
    private Size mPreviewSize;
    private Size mCaptureSize;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private CameraDevice mCameraDevice;
    private TextureView mTextureView;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraManager mCameraManager;
    private CameraCharacteristics mCameraCharacteristics;

    private int shortISO;
    private int longISO;
    private long shorttime;
    private long longtime;
    private long sexposure_time;
    private long lexposure_time;
    private TextView text;
    private static double[][] brightness = new double[2][];
    private double[] result;
    private Range<Integer> isorange;
    private Range<Long> timerange;

    public Handler myHandler=new Handler(){
        @Override
        public void handleMessage(Message msg){
            if(msg.what==0x11){
                Bundle bundle=msg.getData();
                if (brightness[0] == null){
                    brightness[0] = bundle.getDoubleArray("msg");
                }else{
                    brightness[1] = bundle.getDoubleArray("msg");
                }
                if (brightness[1] == null){
                }else{
                    result = division(brightness[0],brightness[1]);
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //声明handler
                            Message msg = new Message();
                            msg.what = 0x11;
                            Bundle bundle = new Bundle();
                            bundle.clear();

                            //转化为Json格式
                            JSONObject clientKey=new JSONObject();
                            JSONArray jsonArray=new JSONArray();
                            for (int i=0;i<result.length;i++) {
                                try {
                                    jsonArray.put(result[i]);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                            try {
                                clientKey.put("data",jsonArray);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            try {
                                URL url = new URL(server_url+tag);
                                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                                httpURLConnection.setRequestMethod("POST");
                                httpURLConnection.setDoOutput(true);
                                httpURLConnection.setDoInput(true);
                                httpURLConnection.setUseCaches(false);
                                httpURLConnection.setRequestMethod("POST");
                                httpURLConnection.setRequestProperty("Content-Type", "application/json");
                                httpURLConnection.setRequestProperty("Charset","UTF-8");
                                httpURLConnection.connect();
                                DataOutputStream dos=new DataOutputStream(httpURLConnection.getOutputStream());
                                dos.writeBytes(clientKey.toString());
                                dos.flush();
                                dos.close();
                                int resultCode=httpURLConnection.getResponseCode();
                                if(HttpURLConnection.HTTP_OK==resultCode){
                                    StringBuffer strb=new StringBuffer();
                                    String readLine=new String();
                                    BufferedReader responseReader=new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                                    while((readLine=responseReader.readLine())!=null){
                                        strb.append(readLine);
                                    }
                                    responseReader.close();
                                    JSONObject jsonObject = new JSONObject(strb.toString());
                                    bundle.putString("msg",jsonObject.getString("success")+"   "+jsonObject.getString("message")+"\n");
                                    msg.setData(bundle);
                                    uploadHandler.sendMessage(msg);
                                }
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    thread.start();
                }
            }
        }
    };
    public Handler uploadHandler=new Handler() {
        public void handleMessage(Message msg){
            if(msg.what==0x11){
                Bundle bundle=msg.getData();
                text.append(bundle.getString("msg"));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //全屏无状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);
        mTextureView = (TextureView) findViewById(R.id.textureView);

        //回调界面
        text = (TextView)findViewById(R.id.text);
        text.setBackgroundColor(Color.argb(100,0,0,0));

        //感光度
        try {
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics("0");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        isorange = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        longISO = isorange.getLower();
        shortISO = isorange.getUpper();

        timerange = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        shorttime = timerange.getLower();
        longtime = timerange.getUpper();

        //拍照

//        final Drawable capture = getResources().getDrawable(R.drawable.btn_capture,null);
//        final Drawable stop = getResources().getDrawable(R.drawable.btn_stopcapture,null);

        btnCapture =(ImageView)findViewById(R.id.photoButton);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sexposure_time = Long.parseLong(exposure_duration)*10;
                lexposure_time = sexposure_time * (shortISO / longISO);
                if (capture_mode.equals("single")){
                    takePicture(v,sexposure_time,shortISO);
                    takePicture(v,lexposure_time,longISO);
                }else{
                    //连拍逻辑
                }
            }
        });


        //设置参数
        btnSetting = (ImageView)findViewById(R.id.btn_setting);
        btnSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(MainActivity.this,SettingActivity.class);
                intent.putExtra("exposure_duration",exposure_duration);
                intent.putExtra("tag",tag);
                intent.putExtra("server_url",server_url);
                intent.putExtra("capture_mode",capture_mode);
                intent.putExtra("store_to_gallery",store_to_gallery);
                intent.putExtra("shorttime",shorttime);
                intent.putExtra("longtime",longtime);
                startActivityForResult(intent,1);
            }
        });
    }

    private double[] division(double[] sbrightness, double[] lbrightness) {
        double[] result = new double[sbrightness.length];
        for (int i=0;i<sbrightness.length;i++){
            result[i] = sbrightness[i]/lbrightness[i];
        }
        return result;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode){
            case 1:
                if(resultCode==RESULT_OK){
                    exposure_duration= data.getStringExtra("exposure_duration");
                    tag= data.getStringExtra("tag");
                    server_url= data.getStringExtra("server_url");
                    capture_mode= data.getStringExtra("capture_mode");
                    store_to_gallery= data.getStringExtra("store_to_gallery");
                }
                break;
            default:
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();
        if (!mTextureView.isAvailable()) {
            mTextureView.setSurfaceTextureListener(mTextureListener);
        } else {
            startPreview();
        }
    }

    private void startCameraThread() {
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    private TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //当SurefaceTexture可用的时候，设置相机参数并打开相机
            setupCamera(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void setupCamera(int width, int height) {
        //获取摄像头的管理者CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //遍历所有摄像头
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                //此处默认打开后置摄像头
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;
                //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                //根据TextureView的尺寸设置预览尺寸
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                //获取相机支持的最大拍照尺寸
                mCaptureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                    @Override
                    public int compare(Size lhs, Size rhs) {
                        return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
                    }
                });
                //此ImageReader用于拍照所需
                setupImageReader();
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //选择sizeMap中大于并且最接近width和height的size
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }


    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private void startPreview() {
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        mCaptureRequest = mCaptureRequestBuilder.build();
                        mCameraCaptureSession = session;
                        mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void takePicture(View view,long exposure_time,int iso) {
        capture(exposure_time,iso);
    }
//    private void lockFocus(long exposure_time,int iso) {
//        try {
//            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
//            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,iso);
//            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);
//            Toast.makeText(getApplicationContext(), "exposure:" + exposure_time, Toast.LENGTH_SHORT).show();
//            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback, mCameraHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }


//    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
//
//
//        @Override
//        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
//        }
//
//        @Override
//        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
//            capture();
//        }
//    };

    private void capture(long exposure_time,int iso) {
        try {
            final CaptureRequest.Builder mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));

            mCaptureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);//
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);//

            //mCaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            mCaptureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,iso);
            mCaptureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);

            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);//

            Toast.makeText(getApplicationContext(), "exposure:" + exposure_time, Toast.LENGTH_SHORT).show();

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    Toast.makeText(getApplicationContext(), "Image Saved!", Toast.LENGTH_SHORT).show();
                    unLockFocus();
                }
            };
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(mCaptureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unLockFocus() {
        try {
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), null, mCameraHandler);
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void setupImageReader() {
        //2代表ImageReader中最多可以获取两帧图像流
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(),
                ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                mCameraHandler.post(new imageSaver(reader.acquireNextImage(),store_to_gallery,myHandler));
            }
        }, mCameraHandler);
    }

    public static class imageSaver implements Runnable {

        private Image mImage;
        private String Storage;
        private Handler handler;

        public imageSaver(Image image,String store_to_gallery,Handler mainHandler) {
            mImage = image;
            Storage = store_to_gallery;
            handler = mainHandler;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            if (Storage.equals("0")){
                manage(data);
                if (mImage !=null){
                    mImage.close();
                }
                //上传代码
            }else{
                manage(data);
                String path = Environment.getExternalStorageDirectory() + "/DCIM/CameraV2/";
                File mImageFile = new File(path);
                if (!mImageFile.exists()) {
                    mImageFile.mkdir();
                }
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fileName = path + "IMG_" + timeStamp + ".jpg";
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(fileName);
                    fos.write(data, 0, data.length);
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
                    if (mImage !=null){
                        mImage.close();
                    }
                }
            }
        }
        private void manage(byte[] bytes){
            Bitmap bitmap= BitmapFactory.decodeByteArray(bytes,0,bytes.length);

            int grey;
            int red;
            int green;
            int blue;
            int height=bitmap.getHeight();
            int width=bitmap.getWidth();
            int[] pixels = new int[width*height];
            bitmap.getPixels(pixels,0,width,0,0,width,height);

            double[] brightness=new double[width];

            for (int i=0;i<width;i++){
                red=0;
                green=0;
                blue=0;
                for (int j=0;j<height;j++){
                    grey  = pixels[height*i+j];
                    red += ((grey & 0x00FF0000)>>16);
                    green += ((grey & 0x0000FF00)>>8);
                    blue += ((grey & 0x000000FF));
                    if(i==1&&j==2){
                        System.out.println("RGB："+((grey & 0x00FF0000)>>16)+ " "+ ((grey & 0x0000FF00)>>8)+" "+((grey & 0x000000FF)));
                    }
                }
                brightness[i]=red*0.299+green*0.587+blue*0.114;
            }
            Message msg = new Message();
            msg.what = 0x11;
            Bundle bundle = new Bundle();
            bundle.clear();
            bundle.putDoubleArray("msg",brightness);
            msg.setData(bundle);
            handler.sendMessage(msg);
        }
    }
}
