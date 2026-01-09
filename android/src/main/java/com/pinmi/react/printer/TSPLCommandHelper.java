package com.pinmi.react.printer;

import static com.pinmi.react.printer.adapter.UtilsImage.getPixelsSlow;
import static com.pinmi.react.printer.adapter.UtilsImage.recollectSlice;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Helper class for generating TSPL (TSC Printer Language) commands
 * This can be extended to call your Kotlin TsplCommandGenerator
 */
public class TSPLCommandHelper {
    private static final String TAG = "TSPLCommandHelper";

    /**
     * Generate a simple TSPL text label command
     * 
     * @param width    Label width in mm
     * @param height   Label height in mm
     * @param gap      Gap between labels in mm
     * @param text     Text to print
     * @param x        X position
     * @param y        Y position
     * @param fontSize Font size (1-8)
     * @return Base64-encoded TSPL commands
     */
    public static String generateSimpleTextLabel(
            double width,
            double height,
            double gap,
            String text,
            int x,
            int y,
            int fontSize) {
        StringBuilder tspl = new StringBuilder();

        // TSPL header commands
        tspl.append(String.format("SIZE %.1f mm, %.1f mm\r\n", width, height));
        tspl.append(String.format("GAP %.1f mm, 0 mm\r\n", gap));
        tspl.append("CLS\r\n");

        // Text command: TEXT
        // x,y,"font",rotation,x-multiplication,y-multiplication,"content"
        // Font: "1"=8x12, "2"=12x20, "3"=16x24, "4"=24x32, "5"=32x48, "6"=14x19,
        // "7"=21x27, "8"=14x25
        tspl.append(String.format("TEXT %d,%d,\"%d\",0,1,1,\"%s\"\r\n", x, y, fontSize, text));

        // Print command
        tspl.append("PRINT 1,1\r\n");

        // Convert to base64
        byte[] tsplBytes = tspl.toString().getBytes(StandardCharsets.US_ASCII);
        String base64 = Base64.encodeToString(tsplBytes, Base64.NO_WRAP);

        Log.d(TAG, "Generated TSPL command: " + tspl.toString().replace("\r\n", "\\r\\n"));

        return base64;
    }

    /**
     * Generate TSPL commands for auto feed and cut operations (similar to ESC/POS printBill)
     * No text printing, just feed and cut commands
     * 
     * @param cut      If true, cut the paper after printing (FORMFEED + CUT command)
     * @param feed     If true, feed to next label (FORMFEED command) - tailingLine
     * @param feedDots Number of dots to feed (ignored when feed=true, use FORMFEED instead)
     * @param eop      If true, use EOP (End Of Print) command instead of PRINT
     * @return Base64-encoded TSPL commands
     */
    public static String generateAutoFeedAndCut(
            boolean cut,
            boolean feed,
            int feedDots,
            boolean eop) {
        StringBuilder tspl = new StringBuilder();

        // Feed to next label before printing if requested (tailingLine)
        // FORMFEED is better for label printers as it uses gap/mark detection
        if (feed) {
            tspl.append("FORMFEED\r\n");
        }

        // Print command (only if not using EOP - EOP must be last)
        if (!eop) {
            tspl.append("PRINT 1,1\r\n");
        }

        // Cut paper after printing if requested (only if not using EOP)
        // Note: CUT command may not be supported by all TSPL printers
        // Use FORMFEED to advance to next label before cutting
        if (cut && !eop) {
            // Feed to next label for cutting
            tspl.append("FORMFEED\r\n");
            // Try CUT command (may not work on all printers)
            // If CUT is not supported, the FORMFEED will position paper for manual cutting
            tspl.append("CUT\r\n");
        }

        // EOP command must be last (End Of Print)
        if (eop) {
            tspl.append("EOP\r\n");
        }

        // Convert to base64
        byte[] tsplBytes = tspl.toString().getBytes(StandardCharsets.US_ASCII);
        String base64 = Base64.encodeToString(tsplBytes, Base64.NO_WRAP);

        Log.d(TAG, "Generated TSPL auto feed and cut command: " + tspl.toString().replace("\r\n", "\\r\\n"));

        return base64;
    }

    /**
     * Convert TSPL command string to base64
     * Use this if you're generating TSPL commands in Kotlin and just need to encode
     * them
     * 
     * @param tsplCommand TSPL command string (e.g., "SIZE 35 mm, 22 mm\r\n...")
     * @return Base64-encoded TSPL commands
     */
    public static String encodeTSPLCommand(String tsplCommand) {
        byte[] tsplBytes = tsplCommand.getBytes(StandardCharsets.US_ASCII);
        return Base64.encodeToString(tsplBytes, Base64.NO_WRAP);
    }

    /**
     * Generate TSPL command from Kotlin TsplCommandGenerator
     * This method can be called from your app's Kotlin code via reflection or
     * direct call
     * 
     * Example usage in your Kotlin code:
     * ```kotlin
     * val tsplGenerator = TsplCommandGenerator()
     * val tsplCommands = tsplGenerator.generateLabelRetail(context, printer,
     * sysFlags, labels)
     * val base64 = TSPLCommandHelper.encodeTSPLCommand(String(tsplCommands,
     * Charsets.US_ASCII))
     * ```
     * 
     * @param tsplCommandBytes ByteArray from Kotlin TsplCommandGenerator
     * @return Base64-encoded TSPL commands
     */
    public static String encodeTSPLCommandFromBytes(byte[] tsplCommandBytes) {
        return Base64.encodeToString(tsplCommandBytes, Base64.NO_WRAP);
    }

    /**
     * Convert bitmap to TSPL BITMAP data format
     * TSPL BITMAP format: Each byte represents 8 pixels (MSB first)
     * 
     * @param bitmap Bitmap to convert (will be converted to monochrome)
     * @param invert If true, invert the bitmap colors (for printers that interpret bit=1 as white)
     * @return Triple of (widthBytes, height, bitmapData)
     */
    public static Triple<Integer, Integer, byte[]> bitmapToTsplData(Bitmap bitmap, boolean invert) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int widthBytes = (width + 7) / 8; // Round up to nearest byte
        byte[] data = new byte[widthBytes * height];

        // Second pass: Apply threshold and convert to TSPL format
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                // Extract RGB values
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);
                // Convert to grayscale using standard luminance formula
                int gray = (int) (0.299 * red + 0.587 * green + 0.114 * blue);

                boolean isBlack = gray < 128;
                // Apply inversion if needed
                if (invert) {
                    isBlack = !isBlack;
                }

                if (isBlack) {
                    int byteIndex = (y * widthBytes) + (x / 8);
                    int bitIndex = 7 - (x % 8); // MSB first
                    data[byteIndex] = (byte) (data[byteIndex] | (1 << bitIndex));
                }
            }
        }

        return new Triple<>(widthBytes, height, data);
    }

    public static String generateImageLabel(
            int gapMM,
            int dotMM,
            Bitmap bitmap,
            int printerWidthMM,
            int printerHeightMM,
            int x,
            int y,
            boolean invert) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null");
            return null;
        }

        // Set SIZE to max printer width (108mm) to prevent auto-centering
        int labelWidthMm = printerWidthMM;
        int labelHeightMm = printerHeightMM;

        int imageX = x;
        int imageY = y;

        // Convert bitmap to TSPL format
        Triple<Integer, Integer, byte[]> bitmapData = bitmapToTsplData(bitmap, invert);
        int widthBytes = bitmapData.first;
        int imgWidth = bitmap.getWidth();
        int imgHeight = bitmap.getHeight();
        byte[] bitmapBytes = bitmapData.third;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        try {
            // TSPL header commands (as ASCII)
            // SIZE should match printer width exactly
            String header = String.format("SIZE %d mm, %d mm\r\n", labelWidthMm, labelHeightMm) +
                    String.format("GAP %d mm, 0 mm\r\n", gapMM) +
                    "CLS\r\n";
            stream.write(header.getBytes(StandardCharsets.US_ASCII));

            // BITMAP command: BITMAP x,y,width_bytes,height,type,
            // type: 0=normal, 1=mirror, 2=upside down, 3=mirror+upside down
            // Use calculated imageX which centers the image
            String bitmapCmd = String.format("BITMAP %d,%d,%d,%d,2,", imageX, imageY, widthBytes, imgHeight);
            stream.write(bitmapCmd.getBytes(StandardCharsets.US_ASCII));

            // Write binary bitmap data directly
            stream.write(bitmapBytes);

            // Print command
            stream.write("\r\nPRINT 1,1\r\n".getBytes(StandardCharsets.US_ASCII));

            Log.d(TAG, "Generated TSPL image label: SIZE=" + labelWidthMm + "x" + labelHeightMm + "mm, " +
                    "image=" + imgWidth + "x" + imgHeight + " pixels, " +
                    "position=(" + imageX + "," + imageY + "), " +
                    "printerWidth=" + printerWidthMM + " dots (" + (printerWidthMM * dotMM) + " ), " +
                    "data=" + bitmapBytes.length + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "Error generating TSPL image label: " + e.getMessage());
            return null;
        }

        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);
    }

    /**
     * Simple Triple class for returning multiple values
     */
    public static class Triple<A, B, C> {
        public final A first;
        public final B second;
        public final C third;

        public Triple(A first, B second, C third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }
}
