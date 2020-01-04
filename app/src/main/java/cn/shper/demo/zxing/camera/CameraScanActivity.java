package cn.shper.demo.zxing.camera;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import cn.shper.demo.zxing.R;
import cn.shper.demo.zxing.base.view.ViewfinderView;
import cn.shper.demo.zxing.camera.internal.CameraManager;

/**
 * Author : shixupan
 * EMail : shixupan10829@hellobike.com
 * Date : 2019-12-31
 */
public class CameraScanActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = CameraScanActivity.class.getSimpleName();

    private CameraManager cameraManager;
    private CameraScanActivityHandler handler;
    private ViewfinderView viewfinderView;
    private TextView resultTxt;

    private boolean hasSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);

        resultTxt = findViewById(R.id.result_txt);

        hasSurface = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        cameraManager = new CameraManager(getApplication());

        viewfinderView = findViewById(R.id.viewfinder_view);
        viewfinderView.setFrame(cameraManager.getFramingRect(), cameraManager.getFramingRectInPreview());

        SurfaceView surfaceView = findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();

        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceHolder);
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            surfaceHolder.addCallback(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // do nothing
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }

        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }

        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = new CameraScanActivityHandler(this, cameraManager);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
        }
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    public void showResult(String text) {
        if (TextUtils.isEmpty(text)) {
            resultTxt.setText("NotFound!!!");
        } else {
            resultTxt.setText(text);
        }
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

}
