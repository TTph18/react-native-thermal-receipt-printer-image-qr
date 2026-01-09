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
import com.facebook.react.bridge.ReadableMap;
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
        this.adapter.init(reactContext, successCallback, errorCallback);
    }

    @ReactMethod
    @Override
    public void closeConn() {
        if (this.adapter == null) {
            this.adapter = USBPrinterAdapter.getInstance();
        }
        this.adapter.closeConnectionIfExists();
    }

    @ReactMethod
    @Override
    public void getDeviceList(Callback successCallback, Callback errorCallback) {
        List<PrinterDevice> printerDevices = adapter.getDeviceList(errorCallback);
        WritableArray pairedDeviceList = Arguments.createArray();
        if (printerDevices.size() > 0) {
            for (PrinterDevice printerDevice : printerDevices) {
                pairedDeviceList.pushMap(printerDevice.toRNWritableMap());
            }
            successCallback.invoke(pairedDeviceList);
        } else {
            errorCallback.invoke("No Device Found");
        }
    }

    @ReactMethod
    @Override
    public void printRawData(String base64Data, Callback errorCallback) {
        adapter.printRawData(base64Data, errorCallback);
    }

    @ReactMethod
    @Override
    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Callback errorCallback) {
        adapter.printImageData(imageUrl, imageWidth, imageHeight, errorCallback);
    }

    @ReactMethod
    @Override
    public void printImageBase64(String base64, int imageWidth, int imageHeight, Callback errorCallback) {
        // Call overloaded method with default values: x=-1 (center align), y=0
        printImageBase64(base64, imageWidth, imageHeight, -1, 0, errorCallback);
    }

    /**
     * Method with x, y positioning for USB printers
     * x: Default -1 = center align, 0 = left align, > 0 = absolute position in dots
     * y: Default 0 = top, vertical position in dots (approximate 24 dots per line)
     */
    @ReactMethod
    public void printImageBase64WithPosition(String base64, int imageWidth, int imageHeight, int x, int y, Callback errorCallback) {
        // String imageBase64 = "data:image/png;base64," + imageUrl;
        // String base64ImageProcessed = imageUrl.split(",")[1];
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        // Cast to USBPrinterAdapter to access the overloaded method with x, y parameters
        if (adapter instanceof USBPrinterAdapter) {
            ((USBPrinterAdapter) adapter).printImageBase64(decodedByte, imageWidth, imageHeight, x, y, errorCallback);
        } else {
            // Fallback to interface method if cast fails (shouldn't happen)
            adapter.printImageBase64(decodedByte, imageWidth, imageHeight, errorCallback);
        }
    }

    @ReactMethod
    public void connectPrinter(Integer vendorId, Integer productId, Callback successCallback, Callback errorCallback) {
        adapter.selectDevice(USBPrinterDeviceId.valueOf(vendorId, productId), successCallback, errorCallback);
    }

    /**
     * Encode TSPL command string to base64
     * Use this if you're generating TSPL commands in Kotlin and just need to encode
     * them
     * 
     * @param tsplCommand     TSPL command string
     * @param successCallback Returns base64-encoded TSPL commands
     * @param errorCallback   Error callback
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
     * @param width         Label width in mm
     * @param height        Label height in mm
     * @param gap           Gap between labels in mm
     * @param text          Text to print
     * @param x             X position
     * @param y             Y position
     * @param fontSize      Font size (1-8)
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
            Callback errorCallback) {
        try {
            String base64TSPL = TSPLCommandHelper.generateSimpleTextLabel(
                    width, height, gap, text, x, y, fontSize);
            adapter.printRawData(base64TSPL, errorCallback);
        } catch (Exception e) {
            errorCallback.invoke("Failed to generate or print TSPL command: " + e.getMessage());
        }
    }

    /**
     * Generate TSPL commands for auto feed and cut operations (returns base64 string)
     * No text printing, just feed and cut commands
     * 
     * @param options       Options map containing:
     *                      - cut (boolean, default: false): Cut paper after printing
     *                      - tailingLine (boolean, default: false): Feed extra paper before printing
     *                      - feedDots (int, default: 50): Number of dots to feed
     *                      - eop (boolean, default: false): Use EOP (End Of Print) command instead of PRINT
     * @param successCallback Returns base64-encoded TSPL commands
     * @param errorCallback Error callback
     */
    @ReactMethod
    public void generateAutoFeedAndCut(ReadableMap options, Callback successCallback, Callback errorCallback) {
        try {
            // Extract options with defaults
            boolean cut = options.hasKey("cut") ? options.getBoolean("cut") : false;
            boolean tailingLine = options.hasKey("tailingLine") ? options.getBoolean("tailingLine") : false;
            int feedDots = options.hasKey("feedDots") ? options.getInt("feedDots") : 50;
            boolean eop = options.hasKey("eop") ? options.getBoolean("eop") : false;

            String base64TSPL = TSPLCommandHelper.generateAutoFeedAndCut(
                    cut, tailingLine, feedDots, eop);
            successCallback.invoke(base64TSPL);
        } catch (Exception e) {
            errorCallback.invoke("Failed to generate TSPL auto feed and cut command: " + e.getMessage());
        }
    }

    /**
     * Print image only with automatic size calculation based on printer width
     * This method calculates the image size based on printer width (58mm or 80mm)
     * and maintains aspect ratio automatically
     * 
     * @param base64Image   Base64-encoded image data
     * @param options       Options map containing:
     *                      - gapMM (double): Gap between labels in mm
     *                      - dotMM (double): Dots per mm
     *                      - printerWidthMM (double): Printer width in mm
     *                      - printerHeightMM (double): Printer height in mm
     *                      - left (int): Left position in dots
     *                      - top (int): Top position in dots
     * @param errorCallback Error callback
     */
    @ReactMethod
    public void printTSPLImageLabel(String base64Image, ReadableMap options, Callback errorCallback) {
        try {
            // Extract options with defaults
            int gapMM = options.hasKey("gapMM") ? options.getInt("gapMM") : 2;
            int dotMM = options.hasKey("dotMM") ? options.getInt("dotMM") : 8;
            int printerWidthMM = options.hasKey("printerWidthMM") ? options.getInt("printerWidthMM") : 50;
            int printerHeightMM = options.hasKey("printerHeightMM") ? options.getInt("printerHeightMM") : 30;
            int left = options.hasKey("left") ? options.getInt("left") : 0;
            int top = options.hasKey("top") ? options.getInt("top") : 0;
            boolean invert = options.hasKey("invert") ? options.getBoolean("invert") : false;

            // Decode base64 image
            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

            if (decodedByte == null) {
                errorCallback.invoke("Failed to decode base64 image");
                return;
            }

            // Initialize adapter if needed
            if (this.adapter == null) {
                this.adapter = USBPrinterAdapter.getInstance();
            }

            // Cast to USBPrinterAdapter to access the printTSPLImageLabel method
            if (this.adapter instanceof USBPrinterAdapter) {
                USBPrinterAdapter usbAdapter = (USBPrinterAdapter) this.adapter;
                usbAdapter.printTSPLImageLabel(decodedByte, gapMM, dotMM, printerWidthMM, printerHeightMM,
                        left, top, invert,
                        errorCallback);
            } else {
                errorCallback.invoke("Adapter is not USBPrinterAdapter instance");
            }
        } catch (Exception e) {
            errorCallback.invoke("Failed to print TSPL image label: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "RNUSBPrinter";
    }
}
