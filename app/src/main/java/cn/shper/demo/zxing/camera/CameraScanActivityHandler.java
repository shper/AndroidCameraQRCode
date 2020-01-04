package cn.shper.demo.zxing.camera;

import android.os.Handler;
import android.os.Message;

import cn.shper.demo.zxing.R;
import cn.shper.demo.zxing.camera.internal.CameraManager;
import cn.shper.demo.zxing.camera.internal.decode.DecodeThread;

/**
 * This class handles all the messaging which comprises the state machine for capture.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraScanActivityHandler extends Handler {

    private final CameraScanActivity activity;
    private final DecodeThread decodeThread;
    private final CameraManager cameraManager;

    public CameraScanActivityHandler(CameraScanActivity activity,
                                     String characterSet,
                                     CameraManager cameraManager) {
        this.activity = activity;
        decodeThread = new DecodeThread(activity, characterSet, null);
        decodeThread.start();

        // Start ourselves capturing previews and decoding.
        this.cameraManager = cameraManager;
        cameraManager.startPreview();
        restartPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case R.id.restart_preview:
                restartPreviewAndDecode();
                break;
        }
    }

    public void quitSynchronously() {
        cameraManager.stopPreview();
        Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
        quit.sendToTarget();
        try {
            // Wait at most half a second; should be enough time, and onPause() will timeout quickly
            decodeThread.join(500L);
        } catch (InterruptedException e) {
            // continue
        }
    }

    private void restartPreviewAndDecode() {
        cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
        activity.drawViewfinder();
    }

}
