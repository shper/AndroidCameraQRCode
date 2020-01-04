package cn.shper.demo.zxing.camera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.zxing.Result;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import cn.shper.demo.zxing.R;
import cn.shper.demo.zxing.base.decode.DecodeFormatManager;
import cn.shper.demo.zxing.base.view.AutoFitTextureView;

import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;

public class Camera2ScanActivity extends AppCompatActivity {

    private static final String TAG = Camera2ScanActivity.class.getSimpleName();

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private static final int sImageFormat = ImageFormat.YUV_420_888;

    private AutoFitTextureView mTextureView;

    private TextView resultTxt;

    private String mCameraId;

    private ImageReader mScanImageReader;

    private CameraDevice mCameraDevice;

    private CaptureRequest.Builder mPreviewBuilder;

    private CameraCaptureSession mSession;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private Semaphore mCapturePicture = new Semaphore(1);

    private Size mPreviewSize;

    private DecodeFormatManager mDecodeFormatManager;

    private HandlerThread mBackgroundThread;

    private Handler mBackgroundHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        mTextureView = findViewById(R.id.preview_view);
        resultTxt = findViewById(R.id.result_txt);

        mDecodeFormatManager = new DecodeFormatManager("utf-8", null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();

        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            } else {
                finish();
            }
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    /**
     * Opens the camera specified by {@link #mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }

        setUpCameraOutputs(width, height);
        configureTransform(width, height);

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mDeviceStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService((CAMERA_SERVICE));
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                // We don't use a front facing camera in this sample.
                if (LENS_FACING_BACK != characteristics.get(LENS_FACING)) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);

                // For still image captures, we use the largest available size.
                List<Size> outputSizes = Arrays.asList(map.getOutputSizes(sImageFormat));
                Size largest = Collections.max(outputSizes, new CompareSizesByArea());

                // Danger, W.R.! Attempting to use too large a preview size could exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);
                Log.e(TAG, "WIDTH: " + mPreviewSize.getWidth() + " HEIGHT: " + mPreviewSize.getHeight());

                mScanImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                        sImageFormat, 2);

                mScanImageReader.setOnImageAvailableListener(mOnScanImageAvailableListener,
                        mBackgroundHandler);

                Point screenParametersPoint = new Point();
                getWindowManager().getDefaultDisplay().getSize(screenParametersPoint);

                mTextureView.setAspectRatio(screenParametersPoint.x,
                        screenParametersPoint.y - getStatusBarHeight());

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {

        if (mTextureView == null || mPreviewSize == null) {
            return;
        }

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mSession) {
                mSession.close();
                mSession = null;
            }

            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            if (null != mScanImageReader) {
                mScanImageReader.close();
                mScanImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            Log.d(TAG, "closeCamera: mCameraOpenCloseLock.release()");
        }
    }

    private CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            cameraCameraCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
            finish();
        }
    };

    private void cameraCameraCaptureSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);
            Surface mImageSurface = mScanImageReader.getSurface();

            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewBuilder.addTarget(mImageSurface);
            mPreviewBuilder.addTarget(surface);

            List<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(mImageSurface);
            surfaceList.add(surface);

            mCameraDevice.createCaptureSession(surfaceList, mSessionPreviewStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.StateCallback mSessionPreviewStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (mCameraDevice == null) {
                return;
            }

            mSession = session;
            try {
                mPreviewBuilder.set(CONTROL_AF_MODE, CONTROL_AF_STATE_ACTIVE_SCAN);
//                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                session.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "onConfigured: ", e);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
    };

    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            mSession = session;
            process(result);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession
                                                session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            mSession = session;
            process(partialResult);
        }

        private void process(CaptureResult partialResult) {
            // DO noting
        }

    };

    private ImageReader.OnImageAvailableListener mOnScanImageAvailableListener = reader -> {
        try (Image image = reader.acquireLatestImage()) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            Result result = mDecodeFormatManager.decode(
                    mDecodeFormatManager.buildYUVLuminanceSource(data, imageWidth, imageHeight, null));

            runOnUiThread(() -> {
                if (result == null || TextUtils.isEmpty(result.getText())) {
                    resultTxt.setText("NotFound!!!");
                } else {
                    resultTxt.setText(result.getText());
                }
            });

            image.close();
            mCapturePicture.release();
        }
    };

    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public int getStatusBarHeight() {
        Rect rectangle = new Rect();
        Window window = getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
        int statusBarHeight = rectangle.top;
        int contentViewTop =
                window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
        int titleBarHeight = contentViewTop - statusBarHeight;

        Log.e(TAG, "StatusBar Height= " + statusBarHeight + " , TitleBar Height = " + titleBarHeight);
        return statusBarHeight + titleBarHeight;
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        try {
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException | NullPointerException e) {
            e.printStackTrace();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

}
