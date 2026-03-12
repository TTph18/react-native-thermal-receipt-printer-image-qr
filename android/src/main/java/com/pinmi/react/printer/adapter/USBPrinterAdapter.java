package com.pinmi.react.printer.adapter;

import static com.pinmi.react.printer.adapter.UtilsImage.getPixelsSlow;
import static com.pinmi.react.printer.adapter.UtilsImage.recollectSlice;
import static com.pinmi.react.printer.adapter.UtilsImage.resizeTheImageForPrinting;
import static com.pinmi.react.printer.adapter.UtilsImage.toGrayscale;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.pinmi.react.printer.TSPLCommandHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * USB Printer Adapter for React Native Thermal Printer
 *
 * Supports both ESC/POS and TSPL (TSC Printer Language) command formats.
 *
 * For TSPL commands:
 * - Use printRawData() with base64-encoded TSPL command strings
 * - TSPL commands are text-based (e.g., "SIZE 35 mm, 22 mm\r\n")
 * - Commands should be encoded as US_ASCII bytes before base64 encoding
 * - Example TSPL command sequence:
 * "SIZE 35 mm, 22 mm\r\nGAP 2 mm, 0 mm\r\nCLS\r\nBITMAP 0,0,280,176,2,<binary
 * data>\r\nPRINT 1,1\r\n"
 *
 * Created by xiesubin on 2017/9/20.
 * Modified to support TSPL commands.
 */

public class USBPrinterAdapter implements PrinterAdapter {
    @SuppressLint("StaticFieldLeak")
    private static USBPrinterAdapter mInstance;

    private final String LOG_TAG = "RNUSBPrinter";
    private Context mContext;
    private UsbManager mUSBManager;
    private PendingIntent mPermissionIndent;
    private UsbDevice mUsbDevice;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbInterface mUsbInterface;
    private UsbEndpoint mEndPoint;
    private static final String ACTION_USB_PERMISSION = "com.pinmi.react.USBPrinter.USB_PERMISSION";
    private static final String EVENT_USB_DEVICE_ATTACHED = "usbAttached";

    private final static char ESC_CHAR = 0x1B;
    private static final byte[] SELECT_BIT_IMAGE_MODE = { 0x1B, 0x2A, 33 };
    private final static byte[] SET_LINE_SPACE_24 = new byte[] { ESC_CHAR, 0x33, 18 };
    private final static byte[] SET_LINE_SPACE_32 = new byte[] { ESC_CHAR, 0x33, 32 };
    private final static byte[] LINE_FEED = new byte[] { 0x0A };
    private static final byte[] LEFT_ALIGN = { 0x1B, 0x61, 0x00 };
    private static final byte[] CENTER_ALIGN = { 0x1B, 0X61, 0X31 };

    /**
     * Generate Relative Print Position command (ESC \)
     *
     * Format: ESC \ nL nH
     * Moves the print position relative to the current position.
     *
     * @param relativePosition The relative position in motion units.
     *                         Positive values move right, negative values move
     *                         left.
     *                         Range: -32768 to 32767
     * @return byte array containing ESC \ nL nH command
     */
    private static byte[] getRelativePrintPosition(int relativePosition) {
        // Clamp the value to valid range: -32768 to 32767
        if (relativePosition > 32767)
            relativePosition = 32767;
        if (relativePosition < -32768)
            relativePosition = -32768;

        int value;
        if (relativePosition >= 0) {
            // Positive: move right
            value = relativePosition;
        } else {
            // Negative: move left (using complement of 65536)
            value = 65536 + relativePosition;
        }

        byte nL = (byte) (value & 0xFF);
        byte nH = (byte) ((value >> 8) & 0xFF);

        return new byte[] { 0x1B, 0x5C, nL, nH };
    }

    /**
     * Generate Left Margin command (GS L)
     *
     * Format: GS L nL nH
     * Sets the left margin from the left edge of the printable area.
     * This command is enabled only when processed at the beginning of a line.
     *
     * @param leftMargin The left margin in motion units.
     *                   Range: 0 to 65535
     * @return byte array containing GS L nL nH command
     */
    private static byte[] getLeftMargin(int leftMargin) {
        // Clamp the value to valid range: 0 to 65535
        if (leftMargin < 0)
            leftMargin = 0;
        if (leftMargin > 65535)
            leftMargin = 65535;

        byte nL = (byte) (leftMargin & 0xFF);
        byte nH = (byte) ((leftMargin >> 8) & 0xFF);

        // GS L: 0x1D 0x4C
        return new byte[] { 0x1D, 0x4C, nL, nH };
    }

    private USBPrinterAdapter() {
    }

    public static USBPrinterAdapter getInstance() {
        if (mInstance == null) {
            mInstance = new USBPrinterAdapter();
        }
        return mInstance;
    }

    private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        assert usbDevice != null;
                        Log.i(LOG_TAG, "USB permission granted for device " + usbDevice.getDeviceId() + ", vendor_id: "
                                + usbDevice.getVendorId() + " product_id: " + usbDevice.getProductId());
                        mUsbDevice = usbDevice;
                        // Close any existing connection to ensure clean state
                        closeConnectionIfExists();
                        // Note: Connection will be established automatically on next printRawData call
                    } else {
                        assert usbDevice != null;
                        Log.w(LOG_TAG, "USB permission denied for device: " + usbDevice.getDeviceName());
                        Toast.makeText(context, "USB permission denied. Please grant permission to use the printer.",
                                Toast.LENGTH_LONG).show();
                        mUsbDevice = null;
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (mUsbDevice != null) {
                    Toast.makeText(context, "USB device has been turned off", Toast.LENGTH_LONG).show();
                    closeConnectionIfExists();
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)
                    || UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                synchronized (this) {
                    if (mContext != null) {
                        ((ReactApplicationContext) mContext)
                                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                .emit(EVENT_USB_DEVICE_ATTACHED, null);
                    }
                }
            }
        }
    };

    @SuppressLint("UnspecifiedImmutableFlag")
    public void init(ReactApplicationContext reactContext, Callback successCallback, Callback errorCallback) {
        this.mContext = reactContext;
        this.mUSBManager = (UsbManager) this.mContext.getSystemService(Context.USB_SERVICE);
        this.mPermissionIndent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        mContext.registerReceiver(mUsbDeviceReceiver, filter);
        Log.v(LOG_TAG, "RNUSBPrinter initialized");
        successCallback.invoke();
    }

    public void closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection.releaseInterface(mUsbInterface);
            mUsbDeviceConnection.close();
            mUsbInterface = null;
            mEndPoint = null;
            mUsbDeviceConnection = null;
        }
    }

    public List<PrinterDevice> getDeviceList(Callback errorCallback) {
        List<PrinterDevice> lists = new ArrayList<>();
        if (mUSBManager == null) {
            errorCallback.invoke("USBManager is not initialized while get device list");
            return lists;
        }

        for (UsbDevice usbDevice : mUSBManager.getDeviceList().values()) {
            lists.add(new USBPrinterDevice(usbDevice));
        }
        return lists;
    }

    @Override
    public void selectDevice(PrinterDeviceId printerDeviceId, Callback successCallback, Callback errorCallback) {
        if (mUSBManager == null) {
            errorCallback.invoke("USBManager is not initialized before select device");
            return;
        }

        USBPrinterDeviceId usbPrinterDeviceId = (USBPrinterDeviceId) printerDeviceId;
        if (mUsbDevice != null && mUsbDevice.getVendorId() == usbPrinterDeviceId.getVendorId()
                && mUsbDevice.getProductId() == usbPrinterDeviceId.getProductId()) {
            Log.i(LOG_TAG, "already selected device, do not need repeat to connect");
            if (!mUSBManager.hasPermission(mUsbDevice)) {
                closeConnectionIfExists();
                mUSBManager.requestPermission(mUsbDevice, mPermissionIndent);
            }
            successCallback.invoke(new USBPrinterDevice(mUsbDevice).toRNWritableMap());
            return;
        }
        closeConnectionIfExists();
        if (mUSBManager.getDeviceList().size() == 0) {
            errorCallback.invoke("Device list is empty, can not choose device");
            return;
        }
        for (UsbDevice usbDevice : mUSBManager.getDeviceList().values()) {
            if (usbDevice.getVendorId() == usbPrinterDeviceId.getVendorId()
                    && usbDevice.getProductId() == usbPrinterDeviceId.getProductId()) {
                Log.v(LOG_TAG, "request for device: vendor_id: " + usbPrinterDeviceId.getVendorId() + ", product_id: "
                        + usbPrinterDeviceId.getProductId());
                closeConnectionIfExists();
                mUSBManager.requestPermission(usbDevice, mPermissionIndent);
                successCallback.invoke(new USBPrinterDevice(usbDevice).toRNWritableMap());
                return;
            }
        }

        errorCallback.invoke("can not find specified device");
        return;
    }

    private boolean openConnection() {
        if (mUsbDevice == null) {
            Log.e(LOG_TAG, "USB Device is not initialized");
            return false;
        }
        if (mUSBManager == null) {
            Log.e(LOG_TAG, "USB Manager is not initialized");
            return false;
        }

        // Check if connection already exists
        if (mUsbDeviceConnection != null) {
            Log.i(LOG_TAG, "USB Connection already connected");
            return true;
        }

        // CRITICAL: Check if permission is granted before attempting to open device
        if (!mUSBManager.hasPermission(mUsbDevice)) {
            Log.e(LOG_TAG, "USB permission not granted for device. Requesting permission...");
            // Request permission asynchronously
            // Note: This will trigger the BroadcastReceiver which will set mUsbDevice
            mUSBManager.requestPermission(mUsbDevice, mPermissionIndent);
            return false;
        }

        // Verify device still exists in the device list
        boolean deviceFound = false;
        for (UsbDevice device : mUSBManager.getDeviceList().values()) {
            if (device.getVendorId() == mUsbDevice.getVendorId() &&
                    device.getProductId() == mUsbDevice.getProductId()) {
                deviceFound = true;
                // Update reference to the current device instance
                mUsbDevice = device;
                break;
            }
        }

        if (!deviceFound) {
            Log.e(LOG_TAG, "USB device no longer available (disconnected or removed)");
            mUsbDevice = null;
            return false;
        }

        try {
            UsbInterface usbInterface = mUsbDevice.getInterface(0);
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                final UsbEndpoint ep = usbInterface.getEndpoint(i);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        UsbDeviceConnection usbDeviceConnection = mUSBManager.openDevice(mUsbDevice);
                        if (usbDeviceConnection == null) {
                            Log.e(LOG_TAG, "Failed to open USB connection - device may be restricted or in use");
                            return false;
                        }
                        if (usbDeviceConnection.claimInterface(usbInterface, true)) {
                            mEndPoint = ep;
                            mUsbInterface = usbInterface;
                            mUsbDeviceConnection = usbDeviceConnection;
                            Log.i(LOG_TAG, "USB device connected successfully");
                            return true;
                        } else {
                            usbDeviceConnection.close();
                            Log.e(LOG_TAG,
                                    "Failed to claim USB interface - device may be in use by another application");
                            return false;
                        }
                    }
                }
            }
            Log.e(LOG_TAG, "No suitable USB endpoint found");
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "USB device access error: " + e.getMessage(), e);
            // Device might have been disconnected or permission revoked
            mUsbDevice = null;
            return false;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unexpected error opening USB connection: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Print raw data (base64 encoded).
     * Supports both ESC/POS and TSPL command formats.
     * For TSPL commands, the data should be base64-encoded TSPL command strings
     * (e.g., "SIZE 35 mm, 22 mm\r\n").
     *
     * @param data          Base64-encoded raw data (can be ESC/POS binary commands
     *                      or TSPL text commands)
     * @param errorCallback Error callback
     */
    public void printRawData(String data, Callback errorCallback) {
        final String rawData = data;
        Log.v(LOG_TAG, "start to print raw data (length: " + (rawData != null ? rawData.length() : 0) + ")");

        if (mUsbDeviceConnection == null || mEndPoint == null) {
            // Check if device is selected
            if (mUsbDevice == null) {
                String msg = "No USB device selected. Please select a device first.";
                Log.e(LOG_TAG, msg);
                errorCallback.invoke(msg);
                return;
            }

            // Check permission before attempting connection
            if (mUSBManager != null && !mUSBManager.hasPermission(mUsbDevice)) {
                String msg = "USB permission not granted. Please grant permission when prompted, then try again.";
                Log.e(LOG_TAG, msg);
                errorCallback.invoke(msg);
                // Request permission
                mUSBManager.requestPermission(mUsbDevice, mPermissionIndent);
                return;
            }

            boolean isConnected = openConnection();
            if (!isConnected) {
                String msg = "Failed to connect to USB device. Please ensure:\n" +
                        "1. USB permission is granted\n" +
                        "2. Device is connected and not in use by another app\n" +
                        "3. Try disconnecting and reconnecting the device";
                Log.e(LOG_TAG, msg);
                errorCallback.invoke(msg);
                return;
            }
        }

        final UsbDeviceConnection connection = mUsbDeviceConnection;
        final UsbEndpoint endpoint = mEndPoint;
        if (connection == null || endpoint == null) {
            Log.e(LOG_TAG, "USB connection or endpoint is null before print thread");
            errorCallback.invoke("USB connection not available");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] bytes = Base64.decode(rawData, Base64.DEFAULT);
                    if (bytes == null || bytes.length == 0) {
                        Log.e(LOG_TAG, "Decoded data is empty");
                        errorCallback.invoke("Decoded data is empty");
                        return;
                    }

                    // Debug: Always log command data for troubleshooting
                    StringBuilder hexDump = new StringBuilder("Data (hex, first 100 bytes): ");
                    int dumpLength = Math.min(bytes.length, 100);
                    for (int i = 0; i < dumpLength; i++) {
                        hexDump.append(String.format("%02X ", bytes[i] & 0xFF));
                        if ((i + 1) % 16 == 0)
                            hexDump.append("\n");
                    }
                    Log.d(LOG_TAG, hexDump.toString());

                    // Detect command type: TSPL vs ESC/POS
                    boolean isESCPOS = false;
                    boolean isTSPL = false;

                    // Check for ESC/POS commands (start with 0x1B ESC character)
                    if (bytes.length > 0 && bytes[0] == 0x1B) {
                        isESCPOS = true;
                    }

                    // Also log as string if it's printable (for TSPL text commands)
                    try {
                        String asString = new String(bytes, "US-ASCII");
                        // Check if it contains TSPL commands
                        isTSPL = asString.contains("SIZE") || asString.contains("PRINT") ||
                                asString.contains("CLS") || asString.contains("BITMAP") ||
                                asString.contains("GAP") || asString.contains("REFERENCE");

                        if (isTSPL) {
                            // Replace control chars for readability
                            String readable = asString.replace("\r", "\\r").replace("\n", "\\n");
                            Log.d(LOG_TAG, "TSPL Data (text): " + readable);

                            // Validate TSPL command structure
                            if (!asString.contains("PRINT")) {
                                Log.w(LOG_TAG,
                                        "WARNING: TSPL data does not contain PRINT command. Printer will not execute without PRINT.");
                            }
                            if (asString.contains("SIZE") && !asString.contains("CLS")) {
                                Log.w(LOG_TAG,
                                        "WARNING: TSPL SIZE command found but CLS command missing. This may cause issues.");
                            }
                        } else if (asString.matches("^[\\x20-\\x7E\\r\\n\\t]+$") && !isESCPOS) {
                            // Printable text but not TSPL
                            String readable = asString.replace("\r", "\\r").replace("\n", "\\n");
                            Log.d(LOG_TAG, "Data (text): " + readable);
                            Log.w(LOG_TAG,
                                    "WARNING: Data appears to be plain text, not TSPL commands. Ensure TSPL commands are being sent.");
                        }
                    } catch (Exception e) {
                        if (!isESCPOS) {
                            Log.d(LOG_TAG, "Data is binary (likely BITMAP data for TSPL)");
                        }
                    }

                    if (isESCPOS) {
                        Log.e(LOG_TAG,
                                "ERROR: ESC/POS commands detected. For TSPL printers, you must send TSPL text commands like:");
                        Log.e(LOG_TAG,
                                "  \"SIZE 35 mm, 22 mm\\r\\nGAP 2 mm, 0 mm\\r\\nCLS\\r\\nBITMAP 0,0,280,176,2,<data>\\r\\nPRINT 1,1\\r\\n\"");
                    }

                    Log.d(LOG_TAG, "Sending " + bytes.length + " bytes to USB printer");

                    // Send data in chunks if needed (some USB devices have max packet size limits)
                    // For TSPL commands, small commands (< 512 bytes) should be sent in one packet
                    // to ensure command integrity
                    int offset = 0;
                    int chunkSize = bytes.length < 512 ? bytes.length : 4096; // Send small TSPL commands in one packet
                    int timeout = 5000; // 5 seconds timeout for TSPL printers (increased from 100ms)

                    while (offset < bytes.length) {
                        if (connection == null || endpoint == null) {
                            Log.e(LOG_TAG, "USB connection lost during transfer");
                            errorCallback.invoke("USB connection lost during transfer");
                            return;
                        }
                        int length = Math.min(chunkSize, bytes.length - offset);
                        int result;

                        // Use offset-based transfer if available (API 21+), otherwise copy to temp
                        // buffer
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            result = connection.bulkTransfer(endpoint, bytes, offset, length, timeout);
                        } else {
                            // For older APIs, copy chunk to temporary buffer
                            byte[] chunk = new byte[length];
                            System.arraycopy(bytes, offset, chunk, 0, length);
                            result = connection.bulkTransfer(endpoint, chunk, length, timeout);
                        }

                        if (result < 0) {
                            String errorMsg = "USB bulk transfer failed with code: " + result;
                            Log.e(LOG_TAG, errorMsg);
                            errorCallback.invoke(errorMsg);
                            return;
                        }

                        // Verify actual bytes transferred
                        if (result != length) {
                            Log.w(LOG_TAG, "Partial transfer: requested " + length + " bytes, transferred " + result
                                    + " bytes");
                            offset += result;
                        } else {
                            offset += length;
                        }

                        Log.d(LOG_TAG, "Sent " + result + " bytes, total: " + offset + "/" + bytes.length);

                        // Small delay between chunks to ensure USB buffer is processed
                        if (offset < bytes.length) {
                            try {
                                Thread.sleep(10); // 10ms delay between chunks
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                Log.w(LOG_TAG, "Transfer interrupted");
                            }
                        }
                    }

                    // Additional delay after sending all data to ensure printer processes TSPL
                    // commands
                    // TSPL printers may need time to parse and execute commands, especially PRINT
                    // commands
                    try {
                        // Longer delay for TSPL printers to process PRINT command
                        Thread.sleep(500); // 500ms delay for TSPL command processing
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    Log.i(LOG_TAG, "Successfully sent " + bytes.length + " bytes to USB printer");

                    // Verify the data contains TSPL PRINT command
                    try {
                        String dataStr = new String(bytes, "US-ASCII");
                        if (dataStr.contains("PRINT")) {
                            Log.d(LOG_TAG, "TSPL PRINT command detected in data");
                        } else {
                            Log.w(LOG_TAG,
                                    "WARNING: No PRINT command found in TSPL data. Printer may not execute without PRINT command.");
                        }
                    } catch (Exception e) {
                        // Not ASCII, skip check
                    }
                } catch (Exception e) {
                    String errorMsg = "Failed to print raw data: " + e.getMessage();
                    Log.e(LOG_TAG, errorMsg, e);
                    errorCallback.invoke(errorMsg);
                }
            }
        }).start();
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            myBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);

            return myBitmap;
        } catch (IOException e) {
            // Log exception
            return null;
        }
    }

    @Override
    public void printImageData(final String imageUrl, int imageWidth, int imageHeight, Callback errorCallback) {
        final Bitmap bitmapImage = getBitmapFromURL(imageUrl);

        if (bitmapImage == null) {
            errorCallback.invoke("image not found");
            return;
        }

        Log.v(LOG_TAG, "start to print image data " + bitmapImage);
        boolean isConnected = openConnection();
        if (isConnected && mUsbDeviceConnection != null && mEndPoint != null) {
            Log.v(LOG_TAG, "Connected to device");
            int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

            int b = mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_24, SET_LINE_SPACE_24.length, 100000);

            b = mUsbDeviceConnection.bulkTransfer(mEndPoint, CENTER_ALIGN, CENTER_ALIGN.length, 100000);

            for (int y = 0; y < pixels.length; y += 24) {
                mUsbDeviceConnection.bulkTransfer(mEndPoint, SELECT_BIT_IMAGE_MODE, SELECT_BIT_IMAGE_MODE.length,
                        100000);

                // Set nL and nH based on the width of the image
                byte[] row = new byte[] { (byte) (0x00ff & pixels[y].length),
                        (byte) ((0xff00 & pixels[y].length) >> 8) };

                mUsbDeviceConnection.bulkTransfer(mEndPoint, row, row.length, 100000);

                for (int x = 0; x < pixels[y].length; x++) {
                    // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                    byte[] slice = recollectSlice(y, x, pixels);
                    mUsbDeviceConnection.bulkTransfer(mEndPoint, slice, slice.length, 100000);
                }

                // Do a line feed, if not the printing will resume on the same line
                mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 100000);
            }

            mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_32, SET_LINE_SPACE_32.length, 100000);
            mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 100000);
        } else {
            String msg = "failed to connected to device";
            Log.v(LOG_TAG, msg);
            errorCallback.invoke(msg);
        }

    }

    @Override
    public void printImageBase64(final Bitmap bitmapImage, int imageWidth, int imageHeight, Callback errorCallback) {
        // Call overloaded method with default values: x=-1 (center align), y=0
        printImageBase64(bitmapImage, imageWidth, imageHeight, -1, 0, errorCallback);
    }

    /**
     * Overloaded method with x, y positioning for USB printers
     * x: Default -1 = center align, 0 = left align, > 0 = absolute position in dots
     * y: Default 0 = top, vertical position in dots (approximate 24 dots per line)
     */
    public void printImageBase64(final Bitmap bitmapImage, int imageWidth, int imageHeight, int x, int y,
                                 Callback errorCallback) {
        if (bitmapImage == null) {
            errorCallback.invoke("image not found");
            return;
        }

        Log.v(LOG_TAG, "start to print image data " + bitmapImage);
        boolean isConnected = openConnection();
        if (isConnected && mUsbDeviceConnection != null && mEndPoint != null) {
            int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

            int b = mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_24, SET_LINE_SPACE_24.length, 100000);

            // Calculate image width in dots (pixels)
            int imageWidthDots = pixels.length > 0 ? pixels[0].length : imageWidth;

            // Set print position based on x parameter
            if (x == 0) {
                // Left align: use LEFT_ALIGN command (ESC a 0)
                b = mUsbDeviceConnection.bulkTransfer(mEndPoint, LEFT_ALIGN, LEFT_ALIGN.length, 100000);
            } else if (x < 0) {
                // Center align: use CENTER_ALIGN command (ESC a 1)
                b = mUsbDeviceConnection.bulkTransfer(mEndPoint, CENTER_ALIGN, CENTER_ALIGN.length, 100000);
            } else {
                // x > 0: use Left Margin command (GS L)
                // Format: GS L nL nH where value = nL + (nH × 256)
                // Range: 0 ≤ nL ≤ 255, 0 ≤ nH ≤ 255, 0 ≤ (nL + (nH × 256)) ≤ 65535

                // Clamp x to valid range
                int leftMargin = x;
                if (leftMargin < 0) leftMargin = 0;
                if (leftMargin > 65535) leftMargin = 65535;

                // Split into low byte (nL) and high byte (nH)
                byte nL = (byte) (leftMargin & 0xFF);
                byte nH = (byte) ((leftMargin >> 8) & 0xFF);

                // Send GS L command: 0x1D 0x4C nL nH
                byte[] setLeftMargin = new byte[] { 0x1D, 0x4C, nL, nH };
                b = mUsbDeviceConnection.bulkTransfer(mEndPoint, setLeftMargin, setLeftMargin.length, 100000);

                // Log for debugging
                Log.d(LOG_TAG, String.format("GS L: x=%d, nL=0x%02X (%d), nH=0x%02X (%d), value=%d",
                        x, nL & 0xFF, nL & 0xFF, nH & 0xFF, nH & 0xFF, leftMargin));
            }

            for (int rowIdx = 0; rowIdx < pixels.length; rowIdx += 24) {
                mUsbDeviceConnection.bulkTransfer(mEndPoint, SELECT_BIT_IMAGE_MODE, SELECT_BIT_IMAGE_MODE.length,
                        100000);

                // Set nL and nH based on the width of the image
                byte[] row = new byte[] { (byte) (0x00ff & pixels[rowIdx].length),
                        (byte) ((0xff00 & pixels[rowIdx].length) >> 8) };

                mUsbDeviceConnection.bulkTransfer(mEndPoint, row, row.length, 100000);

                for (int colIdx = 0; colIdx < pixels[rowIdx].length; colIdx++) {
                    // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                    byte[] slice = recollectSlice(rowIdx, colIdx, pixels);
                    mUsbDeviceConnection.bulkTransfer(mEndPoint, slice, slice.length, 100000);
                }

                // Do a line feed, if not the printing will resume on the same line
                mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 100000);
            }

            mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_32, SET_LINE_SPACE_32.length, 100000);
            mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 100000);
        } else {
            String msg = "failed to connected to device";
            Log.v(LOG_TAG, msg);
            errorCallback.invoke(msg);
        }

    }

    public void printTSPLImageLabel(final Bitmap bitmapImage, int gapMM, int dotMM, int printerWidthMM,
                                    int printerHeightMM, int left,
                                    int top,
                                    boolean invert,
                                    Callback errorCallback) {
        if (bitmapImage == null) {
            errorCallback.invoke("bitmap image is null");
            return;
        }

        try {
            Bitmap grayBitmap = toGrayscale(bitmapImage);
            // Use UtilsImage to resize the image to printer width (maintains aspect ratio)
            // printerWidth is already in pixels, height will be auto-calculated (0)
            int printerWidth = printerWidthMM * dotMM;
            int printerHeight = printerHeightMM * dotMM;
            Bitmap resizedBitmap = resizeTheImageForPrinting(grayBitmap, printerWidth, 0);

            if (resizedBitmap == null) {
                errorCallback.invoke("Failed to resize image");
                return;
            }
            // Standard gap between labels

            // Generate TSPL commands with resized image
            // generateImageLabel will calculate mm from bitmap pixels automatically
            String base64TSPL = TSPLCommandHelper.generateImageLabel(
                    gapMM, dotMM, resizedBitmap, printerWidthMM, printerHeightMM, left, top, invert);

            if (base64TSPL == null) {
                errorCallback.invoke("Failed to generate TSPL image label");
                return;
            }

            // Print using printRawData which handles TSPL commands
            printRawData(base64TSPL, errorCallback);

        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to print TSPL image label: " + e.getMessage());
            errorCallback.invoke("Failed to print TSPL image label: " + e.getMessage());
        }
    }
}
