package cn.shper.demo.zxing.camera;

import android.os.Handler;
import android.os.Message;

import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;

import cn.shper.demo.zxing.R;
import cn.shper.demo.zxing.base.decode.DecodeFormatManager;
import cn.shper.demo.zxing.camera.internal.CameraManager;

/**
 * This class handles all the messaging which comprises the state machine for capture.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraScanActivityHandler extends Handler {

    private final CameraScanActivity activity;
    private final CameraManager cameraManager;
    private final DecodeFormatManager decodeFormatManager;

    public CameraScanActivityHandler(CameraScanActivity activity,
                                     CameraManager cameraManager) {
        this.activity = activity;
        decodeFormatManager = new DecodeFormatManager("utf-8", null);

        // Start ourselves capturing previews and decoding.
        this.cameraManager = cameraManager;
        cameraManager.startPreview();
        restartPreviewAndDecode("");
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case R.id.restart_preview:
                restartPreviewAndDecode((String) message.obj);
                break;
            case R.id.decode:
                decode((byte[]) message.obj, message.arg1, message.arg2);
        }
    }

    public void quitSynchronously() {
        cameraManager.stopPreview();
        Message quit = Message.obtain(this, R.id.quit);
        quit.sendToTarget();
    }

    private void restartPreviewAndDecode(String text) {
        cameraManager.requestPreviewFrame(this, R.id.decode);
        activity.drawViewfinder();

        activity.showResult(text);
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency,
     * reuse the same reader objects from one decode to the next.
     *
     * @param data   The YUV preview frame.
     * @param width  The width of the preview frame.
     * @param height The height of the preview frame.
     */
    private void decode(byte[] data, int width, int height) {
        PlanarYUVLuminanceSource source = decodeFormatManager.buildYUVLuminanceSource(data, width, height,
                null);

        Result result = decodeFormatManager.decode(source);
        String text = "";
        if (result != null) {
            text = result.getText();
        }

        // 重新扫描
        Handler handler = activity.getHandler();
        if (handler != null) {
            Message message = Message.obtain(handler, R.id.restart_preview, text);
            message.sendToTarget();
        }
    }

}
