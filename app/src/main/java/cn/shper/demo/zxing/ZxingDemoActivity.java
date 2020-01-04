package cn.shper.demo.zxing;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import cn.shper.demo.zxing.camera.CameraScanActivity;
import cn.shper.demo.zxing.camera2.Camera2ScanActivity;
import cn.shper.demo.zxing.camerax.CameraXScanActivity;

/**
 * Author : shixupan
 * EMail : shixupan10829@hellobike.com
 * Date : 2019-12-31
 */
public class ZxingDemoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zxing_demo);

        findViewById(R.id.camera_btn).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setClass(ZxingDemoActivity.this, CameraScanActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.camera2_btn).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setClass(ZxingDemoActivity.this, Camera2ScanActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.camerax_btn).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setClass(ZxingDemoActivity.this, CameraXScanActivity.class);
            startActivity(intent);
        });
    }

}
