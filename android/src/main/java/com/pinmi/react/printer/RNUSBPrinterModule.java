package com.pinmi.react.printer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.pinmi.react.printer.adapter.PrinterAdapter;
import com.pinmi.react.printer.adapter.PrinterDevice;
import com.pinmi.react.printer.adapter.USBPrinterAdapter;
import com.pinmi.react.printer.adapter.USBPrinterDeviceId;

import java.util.List;

/**
 * Created by xiesubin on 2017/9/22.
 */

public class RNUSBPrinterModule extends ReactContextBaseJavaModule implements RNPrinterModule {

    protected ReactApplicationContext reactContext;

    protected PrinterAdapter adapter;

    public RNUSBPrinterModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @ReactMethod
    @Override
    public void init(Callback successCallback, Callback errorCallback) {
        this.adapter = USBPrinterAdapter.getInstance();
        this.adapter.init(reactContext,  successCallback, errorCallback);
    }

    @ReactMethod
    @Override
    public void closeConn()  {
        if (this.adapter == null) {
            this.adapter = USBPrinterAdapter.getInstance();
        }
        this.adapter.closeConnectionIfExists();
    }

    @ReactMethod
    @Override
    public void getDeviceList(Callback successCallback, Callback errorCallback)  {
        List<PrinterDevice> printerDevices = adapter.getDeviceList(errorCallback);
        WritableArray pairedDeviceList = Arguments.createArray();
        if(printerDevices.size() > 0) {
            for (PrinterDevice printerDevice : printerDevices) {
                pairedDeviceList.pushMap(printerDevice.toRNWritableMap());
            }
            successCallback.invoke(pairedDeviceList);
        }else{
            errorCallback.invoke("No Device Found");
        }
    }

    @ReactMethod
    @Override
    public void printRawData(String base64Data, Callback errorCallback){
        adapter.printRawData(base64Data, errorCallback);
    }

    @ReactMethod
    @Override
    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Callback errorCallback) {
        adapter.printImageData(imageUrl, imageWidth, imageHeight,errorCallback);
    }

    @ReactMethod
    @Override
    public void printImageBase64(String base64, int imageWidth, int imageHeight, Callback errorCallback) {
        // String imageBase64 = "data:image/png;base64," + imageUrl;
        // String base64ImageProcessed = imageUrl.split(",")[1];
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        adapter.printImageBase64(decodedByte, imageWidth, imageHeight,errorCallback);
    }

    @ReactMethod
    public void connectPrinter(Integer vendorId, Integer productId, Callback successCallback, Callback errorCallback) {
        adapter.selectDevice(USBPrinterDeviceId.valueOf(vendorId, productId), successCallback, errorCallback);
    }

    /**
     * Generate TSPL command for simple text label and return as base64
     * This generates TSPL commands on the Kotlin/Java side
     * 
     * @param width Label width in mm
     * @param height Label height in mm
     * @param gap Gap between labels in mm
     * @param text Text to print
     * @param x X position
     * @param y Y position
     * @param fontSize Font size (1-8)
     * @param successCallback Returns base64-encoded TSPL commands
     * @param errorCallback Error callback
     */
    @ReactMethod
    public void generateTSPLTextLabel(
            double width,
            double height,
            double gap,
            String text,
            int x,
            int y,
            int fontSize,
            Callback successCallback,
            Callback errorCallback
    ) {
        try {
            String base64TSPL = TSPLCommandHelper.generateSimpleTextLabel(
                    width, height, gap, text, x, y, fontSize
            );
            successCallback.invoke(base64TSPL);
        } catch (Exception e) {
            errorCallback.invoke("Failed to generate TSPL command: " + e.getMessage());
        }
    }

    /**
     * Encode TSPL command string to base64
     * Use this if you're generating TSPL commands in Kotlin and just need to encode them
     * 
     * @param tsplCommand TSPL command string
     * @param successCallback Returns base64-encoded TSPL commands
     * @param errorCallback Error callback
     */
    @ReactMethod
    public void encodeTSPLCommand(String tsplCommand, Callback successCallback, Callback errorCallback) {
        try {
            String base64 = TSPLCommandHelper.encodeTSPLCommand(tsplCommand);
            successCallback.invoke(base64);
        } catch (Exception e) {
            errorCallback.invoke("Failed to encode TSPL command: " + e.getMessage());
        }
    }

    /**
     * Generate TSPL and print directly
     * Convenience method that generates TSPL command and prints it in one call
     * 
     * @param width Label width in mm
     * @param height Label height in mm
     * @param gap Gap between labels in mm
     * @param text Text to print
     * @param x X position
     * @param y Y position
     * @param fontSize Font size (1-8)
     * @param errorCallback Error callback
     */
    @ReactMethod
    public void printTSPLTextLabel(
            double width,
            double height,
            double gap,
            String text,
            int x,
            int y,
            int fontSize,
            Callback errorCallback
    ) {
        try {
            String base64TSPL = TSPLCommandHelper.generateSimpleTextLabel(
                    width, height, gap, text, x, y, fontSize
            );
            adapter.printRawData(base64TSPL, errorCallback);
        } catch (Exception e) {
            errorCallback.invoke("Failed to generate or print TSPL command: " + e.getMessage());
        }
    }

    /**
     * Generate TSPL label with image from base64 image data
     * Image dimensions are automatically detected from the bitmap
     * 
     * @param base64Image Base64-encoded image data
     * @param labelWidth Label width in mm (0 = auto-calculate from image)
     * @param labelHeight Label height in mm (0 = auto-calculate from image)
     * @param gap Gap between labels in mm
     * @param x X position for image
     * @param y Y position for image
     * @param successCallback Returns base64-encoded TSPL commands
     * @param errorCallback Error callback
     */
    @ReactMethod
    public void generateTSPLImageLabel(
            String base64Image,
            double labelWidth,
            double labelHeight,
            double gap,
            int x,
            int y,
            Callback successCallback,
            Callback errorCallback
    ) {
        try {
            // Decode base64 image to bitmap
            byte[] imageBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            
            if (bitmap == null) {
                errorCallback.invoke("Failed to decode image from base64");
                return;
            }
            
            // Generate TSPL - width/height will be auto-calculated if 0
            String base64TSPL = TSPLCommandHelper.generateImageLabel(
                    labelWidth, labelHeight, gap, bitmap, x, y
            );
            
            if (base64TSPL == null) {
                errorCallback.invoke("Failed to generate TSPL image label");
                return;
            }
            
            successCallback.invoke(base64TSPL);
        } catch (Exception e) {
            errorCallback.invoke("Failed to generate TSPL image label: " + e.getMessage());
        }
    }

    /**
     * Generate TSPL label with image from image URL
     * Image dimensions are automatically detected from the bitmap
     * 
     * @param imageUrl URL of the image
     * @param labelWidth Label width in mm (0 = auto-calculate from image)
     * @param labelHeight Label height in mm (0 = auto-calculate from image)
     * @param gap Gap between labels in mm
     * @param x X position for image
     * @param y Y position for image
     * @param successCallback Returns base64-encoded TSPL commands
     * @param errorCallback Error callback
     */
    @ReactMethod
    public void generateTSPLImageLabelFromURL(
            String imageUrl,
            double labelWidth,
            double labelHeight,
            double gap,
            int x,
            int y,
            Callback successCallback,
            Callback errorCallback
    ) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Download image from URL
                    URL url = new URL(imageUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    
                    if (bitmap == null) {
                        errorCallback.invoke("Failed to load image from URL: " + imageUrl);
                        return;
                    }
                    
                    // Generate TSPL - width/height will be auto-calculated if 0
                    String base64TSPL = TSPLCommandHelper.generateImageLabel(
                            labelWidth, labelHeight, gap, bitmap, x, y
                    );
                    
                    if (base64TSPL == null) {
                        errorCallback.invoke("Failed to generate TSPL image label");
                        return;
                    }
                    
                    successCallback.invoke(base64TSPL);
                } catch (Exception e) {
                    errorCallback.invoke("Failed to generate TSPL image label from URL: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Generate TSPL label with image and text, then print directly
     * Image dimensions are automatically detected from the bitmap
     * 
     * @param base64Image Base64-encoded image data (can be null/empty)
     * @param labelWidth Label width in mm (0 = auto-calculate from image)
     * @param labelHeight Label height in mm (0 = auto-calculate from image)
     * @param gap Gap between labels in mm
     * @param imageX X position for image
     * @param imageY Y position for image
     * @param text Text to print (can be null/empty)
     * @param textX X position for text
     * @param textY Y position for text
     * @param fontSize Font size (1-8)
     * @param errorCallback Error callback
     */
    @ReactMethod
    public void printTSPLImageLabel(
            String base64Image,
            double labelWidth,
            double labelHeight,
            double gap,
            int imageX,
            int imageY,
            String text,
            int textX,
            int textY,
            int fontSize,
            Callback errorCallback
    ) {
        try {
            Bitmap bitmap = null;
            if (base64Image != null && !base64Image.isEmpty()) {
                byte[] imageBytes = Base64.decode(base64Image, Base64.DEFAULT);
                bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (bitmap == null) {
                    errorCallback.invoke("Failed to decode image from base64");
                    return;
                }
            }
            
            // Generate TSPL - width/height will be auto-calculated if 0
            String base64TSPL = TSPLCommandHelper.generateLabelWithImageAndText(
                    labelWidth, labelHeight, gap, bitmap, imageX, imageY,
                    text, textX, textY, fontSize
            );
            
            if (base64TSPL == null) {
                errorCallback.invoke("Failed to generate TSPL label");
                return;
            }
            
            adapter.printRawData(base64TSPL, errorCallback);
        } catch (Exception e) {
            errorCallback.invoke("Failed to generate or print TSPL image label: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "RNUSBPrinter";
    }
}
