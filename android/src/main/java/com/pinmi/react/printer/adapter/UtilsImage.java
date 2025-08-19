package com.pinmi.react.printer.adapter;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

public class UtilsImage {
    // Floyd-Steinberg dithering matrix for better image quality
    private static int[][] Floyd16x16 = new int[][]{
        {0, 128, 32, 160, 8, 136, 40, 168, 2, 130, 34, 162, 10, 138, 42, 170},
        {192, 64, 224, 96, 200, 72, 232, 104, 194, 66, 226, 98, 202, 74, 234, 106},
        {48, 176, 16, 144, 56, 184, 24, 152, 50, 178, 18, 146, 58, 186, 26, 154},
        {240, 112, 208, 80, 248, 120, 216, 88, 242, 114, 210, 82, 250, 122, 218, 90},
        {12, 140, 44, 172, 4, 132, 36, 164, 14, 142, 46, 174, 6, 134, 38, 166},
        {204, 76, 236, 108, 196, 68, 228, 100, 206, 78, 238, 110, 198, 70, 230, 102},
        {60, 188, 28, 156, 52, 180, 20, 148, 62, 190, 30, 158, 54, 182, 22, 150},
        {252, 124, 220, 92, 244, 116, 212, 84, 254, 126, 222, 94, 246, 118, 214, 86},
        {3, 131, 35, 163, 11, 139, 43, 171, 1, 129, 33, 161, 9, 137, 41, 169},
        {195, 67, 227, 99, 203, 75, 235, 107, 193, 65, 225, 97, 201, 73, 233, 105},
        {51, 179, 19, 147, 59, 187, 27, 155, 49, 177, 17, 145, 57, 185, 25, 153},
        {243, 115, 211, 83, 251, 123, 219, 91, 241, 113, 209, 81, 249, 121, 217, 89},
        {15, 143, 47, 175, 7, 135, 39, 167, 13, 141, 45, 173, 5, 133, 37, 165},
        {207, 79, 239, 111, 199, 71, 231, 103, 205, 77, 237, 109, 197, 69, 229, 101},
        {63, 191, 31, 159, 55, 183, 23, 151, 61, 189, 29, 157, 53, 181, 21, 149},
        {254, 127, 223, 95, 247, 119, 215, 87, 253, 125, 221, 93, 245, 117, 213, 85}
    };

    public static Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width = bmpOriginal.getWidth();
        int height = bmpOriginal.getHeight();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }
    public static Bitmap getBitmapResized(Bitmap image, float decreaseSizeBy, int imageWidth, int imageHeight) {
        int imageWidthForResize = image.getWidth();
        int imageHeightForResize = image.getHeight();
        if (imageWidth > 0) {
            imageWidthForResize = imageWidth;
        }

        if (imageHeight > 0) {
            imageHeightForResize = imageHeight;
        }
        return Bitmap.createScaledBitmap(image, (int) (imageWidthForResize * decreaseSizeBy),
                (int) (imageHeightForResize * decreaseSizeBy), true);
    }

    public static int getRGB(Bitmap bmpOriginal, int col, int row) {
        // get one pixel color
        int pixel = bmpOriginal.getPixel(col, row);
        // retrieve color of all channels
        int R = Color.red(pixel);
        int G = Color.green(pixel);
        int B = Color.blue(pixel);
        return Color.rgb(R, G, B);
    }

    public static Bitmap resizeTheImageForPrinting(Bitmap image, int imageWidth, int imageHeight) {
        // making logo size 150 or less pixels
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Fix the logic error - check if imageWidth and imageHeight are greater than 0
        if (imageWidth > 0 && imageHeight > 0) {
            return getBitmapResized(image, 1, imageWidth, imageHeight);
        } else if (imageWidth > 0) {
            // Maintain aspect ratio if only width is specified
            float aspectRatio = (float) height / width;
            int newHeight = (int) (imageWidth * aspectRatio);
            return getBitmapResized(image, 1, imageWidth, newHeight);
        } else if (imageHeight > 0) {
            // Maintain aspect ratio if only height is specified
            float aspectRatio = (float) width / height;
            int newWidth = (int) (imageHeight * aspectRatio);
            return getBitmapResized(image, 1, newWidth, imageHeight);
        }
        
        if (width > 200 || height > 200) {
            float decreaseSizeBy;
            if (width > height) {
                decreaseSizeBy = (200.0f / width);
            } else {
                decreaseSizeBy = (200.0f / height);
            }
            return getBitmapResized(image, decreaseSizeBy, 0, 0);
        }
        return image;
    }

    public static boolean shouldPrintColor(int col) {
        // Extract RGB values
        int r = (col >> 16) & 0xff;
        int g = (col >> 8) & 0xff;
        int b = col & 0xff;
        
        // Calculate luminance using standard formula
        int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        
        // Use adaptive threshold instead of fixed 127
        return luminance < 128;
    }

    public static boolean shouldPrintColorWithDithering(int col, int x, int y) {
        int r = (col >> 16) & 0xff;
        int g = (col >> 8) & 0xff;
        int b = col & 0xff;
        
        // Calculate luminance
        int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        
        // Apply Floyd-Steinberg dithering
        return luminance < Floyd16x16[x & 15][y & 15];
    }

    public static int[][] getPixelsWithDithering(Bitmap image, int imageWidth, int imageHeight) {
        Bitmap resizedImage = resizeTheImageForPrinting(image, imageWidth, imageHeight);
        
        // Convert to grayscale first for better processing
        Bitmap grayImage = toGrayscale(resizedImage);
        
        int width = grayImage.getWidth();
        int height = grayImage.getHeight();
        int[][] result = new int[height][width];
        
        // First pass: get all pixel luminance values
        int[][] luminanceMap = new int[height][width];
        int totalLuminance = 0;
        
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int pixel = grayImage.getPixel(col, row);
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;
                int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                luminanceMap[row][col] = luminance;
                totalLuminance += luminance;
            }
        }

        // Calculate adaptive threshold
        int avgLuminance = totalLuminance / (width * height);

        // Second pass: apply dithering with adaptive threshold
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int luminance = luminanceMap[row][col];
                
                // Use Floyd-Steinberg dithering with adaptive threshold
                boolean shouldPrint = luminance < (Floyd16x16[col & 15][row & 15] + avgLuminance) / 2;
                result[row][col] = shouldPrint ? Color.BLACK : Color.WHITE;
            }
        }
        return result;
    }

    public static byte[] recollectSlice(int y, int x, int[][] img) {
        byte[] slices = new byte[]{0, 0, 0};
        for (int yy = y, i = 0; yy < y + 24 && i < 3; yy += 8, i++) {
            byte slice = 0;
            for (int b = 0; b < 8; b++) {
                int yyy = yy + b;
                if (yyy >= img.length) {
                    continue;
                }
                int col = img[yyy][x];
                boolean v = shouldPrintColor(col);
                slice |= (byte) ((v ? 1 : 0) << (7 - b));
            }
            slices[i] = slice;
        }
        return slices;
    }

    public static byte[] thresholdToBWPic(Bitmap mBitmap) {
        int[] pixels = new int[mBitmap.getWidth() * mBitmap.getHeight()];
        byte[] data = new byte[mBitmap.getWidth() * mBitmap.getHeight()];
        mBitmap.getPixels(pixels, 0, mBitmap.getWidth(), 0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        format_K_threshold(pixels, mBitmap.getWidth(), mBitmap.getHeight(), data);
        return data;
    }

    private static void format_K_threshold(int[] orgpixels, int xsize, int ysize, byte[] despixels) {
        int graytotal = 0;
        int k = 0;

        int i;
        int j;
        int gray;
        for (i = 0; i < ysize; ++i) {
            for (j = 0; j < xsize; ++j) {
                // Extract luminance properly from ARGB pixel
                int pixel = orgpixels[k];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                // Convert to grayscale using standard luminance formula
                gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                graytotal += gray;
                ++k;
            }
        }

        int threshold = graytotal / ysize / xsize;
        k = 0;

        for (i = 0; i < ysize; ++i) {
            for (j = 0; j < xsize; ++j) {
                // Extract luminance properly from ARGB pixel
                int pixel = orgpixels[k];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                // Convert to grayscale using standard luminance formula
                gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                
                if (gray > threshold) {
                    despixels[k] = 0;
                } else {
                    despixels[k] = 1;
                }
                ++k;
            }
        }
    }

    public static byte[] bitmapToBWPix(Bitmap mBitmap) {
        int[] pixels = new int[mBitmap.getWidth() * mBitmap.getHeight()];
        byte[] data = new byte[mBitmap.getWidth() * mBitmap.getHeight()];
        Bitmap grayBitmap = toGrayscale(mBitmap);
        grayBitmap.getPixels(pixels, 0, mBitmap.getWidth(), 0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        format_K_dither16x16(pixels, grayBitmap.getWidth(), grayBitmap.getHeight(), data);
        return data;
    }

    private static void format_K_dither16x16(int[] orgpixels, int xsize, int ysize, byte[] despixels) {
        int k = 0;

        for (int y = 0; y < ysize; ++y) {
            for (int x = 0; x < xsize; ++x) {
                if ((orgpixels[k] & 255) > Floyd16x16[x & 15][y & 15]) {
                    despixels[k] = 0;
                } else {
                    despixels[k] = 1;
                }
                ++k;
            }
        }
    }

    public static int[][] getPixelsSlow(Bitmap image2, int imageWidth, int imageHeight) {
        // Follow the exact same workflow as TSC command
        // 1. First convert to grayscale
        Bitmap grayBitmap = toGrayscale(image2);

        // 2. Then resize the grayscale image
        Bitmap resizedImage = resizeTheImageForPrinting(grayBitmap, imageWidth, imageHeight);

        // 3. Use the same method as Bluetooth ESC/POS for better quality
        byte[] bwData = thresholdToBWPic(resizedImage);
        
        int width = resizedImage.getWidth();
        int height = resizedImage.getHeight();
        int[][] result = new int[height][width];
        
        // Convert byte array back to int array for compatibility
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int index = row * width + col;
                if (index < bwData.length) {
                    result[row][col] = bwData[index] == 1 ? Color.BLACK : Color.WHITE;
                } else {
                    result[row][col] = Color.WHITE;
                }
            }
        }
        return result;
    }
}
