package cn.shper.demo.zxing.base.decode;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class DecodeFormatManager {

    private static final String TAG = DecodeFormatManager.class.getSimpleName();

    private static final Set<BarcodeFormat> PRODUCT_FORMATS;
    private static final Set<BarcodeFormat> INDUSTRIAL_FORMATS;
    private static final Set<BarcodeFormat> ONE_D_FORMATS;
    private static final Set<BarcodeFormat> QR_CODE_FORMATS = EnumSet.of(BarcodeFormat.QR_CODE);
    private static final Set<BarcodeFormat> DATA_MATRIX_FORMATS = EnumSet.of(BarcodeFormat.DATA_MATRIX);
    private static final Set<BarcodeFormat> AZTEC_FORMATS = EnumSet.of(BarcodeFormat.AZTEC);
//    private static final Set<BarcodeFormat> PDF417_FORMATS = EnumSet.of(BarcodeFormat.PDF_417);

    static {
        PRODUCT_FORMATS = EnumSet.of(BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.RSS_14,
                BarcodeFormat.RSS_EXPANDED);
        INDUSTRIAL_FORMATS = EnumSet.of(BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODE_128,
                BarcodeFormat.ITF,
                BarcodeFormat.CODABAR);
        ONE_D_FORMATS = EnumSet.copyOf(PRODUCT_FORMATS);
        ONE_D_FORMATS.addAll(INDUSTRIAL_FORMATS);
    }

//    private static final Map<String, Set<BarcodeFormat>> FORMATS_FOR_MODE;
//
//    static {
//        FORMATS_FOR_MODE = new HashMap<>();
//        FORMATS_FOR_MODE.put(Intents.Scan.ONE_D_MODE, ONE_D_FORMATS);
//        FORMATS_FOR_MODE.put(Intents.Scan.PRODUCT_MODE, PRODUCT_FORMATS);
//        FORMATS_FOR_MODE.put(Intents.Scan.QR_CODE_MODE, QR_CODE_FORMATS);
//        FORMATS_FOR_MODE.put(Intents.Scan.DATA_MATRIX_MODE, DATA_MATRIX_FORMATS);
//        FORMATS_FOR_MODE.put(Intents.Scan.AZTEC_MODE, AZTEC_FORMATS);
//        FORMATS_FOR_MODE.put(Intents.Scan.PDF417_MODE, PDF417_FORMATS);
//    }

    private Map<DecodeHintType, Object> mHints;
    private MultiFormatReader mMultiFormatReader;

    public DecodeFormatManager(String characterSet,
                               ResultPointCallback resultPointCallback) {
        initHints(characterSet, resultPointCallback);
        initMultiFormatReader();
    }

    private void initHints(String characterSet,
                           ResultPointCallback resultPointCallback) {

        mHints = new EnumMap<>(DecodeHintType.class);

        // The prefs can't change while the thread is running, so pick them up once here.
        Collection<BarcodeFormat> decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
        decodeFormats.addAll(DecodeFormatManager.PRODUCT_FORMATS);
        decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
        decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
        decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
        decodeFormats.addAll(DecodeFormatManager.AZTEC_FORMATS);
//        decodeFormats.addAll(DecodeFormatManager.PDF417_FORMATS);

        mHints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);

        if (characterSet != null) {
            mHints.put(DecodeHintType.CHARACTER_SET, characterSet);
        }

        if (resultPointCallback != null) {
            mHints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
        }
    }

    public void initMultiFormatReader() {
        mMultiFormatReader = new MultiFormatReader();
        mMultiFormatReader.setHints(mHints);
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on the format
     * of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildYUVLuminanceSource(byte[] data, int width, int height, Rect rect) {
        if (rect == null) {
            rect = new Rect(0, 0, width, height);
        }

        Log.d(TAG, "data.length: " + data.length
                + " ;width: " + width + " ;height: " + height
                + " ;rect.left: " + rect.left
                + " ;rect.top: " + rect.top
                + " ;rect.width: " + rect.width()
                + " ;rect.height: " + rect.height());

        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
                rect.width(), rect.height(), false);
    }

    public RGBLuminanceSource buildRGBLuminanceSource(byte[] data, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
//        options.inJustDecodeBounds = true;
        // BitmapFactory.decodeByteArray(data, 0, data.length, options);
        // options.inSampleSize = computeSampleSize(options, -1, 1920 * 1080);

        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        // bitmap.recycle();

        return new RGBLuminanceSource(width, height, pixels);
    }

    public Result decode(LuminanceSource source) {
        if (source == null) {
            return null;
        }

        Result result = null;
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            result = mMultiFormatReader.decodeWithState(bitmap);
            Log.d(TAG, "Decode: result=" + result.getText());
        } catch (ReaderException re) {
            Log.e(TAG, "Decode: ", re);
        } finally {
            mMultiFormatReader.reset();
        }

        return result;
    }

}
