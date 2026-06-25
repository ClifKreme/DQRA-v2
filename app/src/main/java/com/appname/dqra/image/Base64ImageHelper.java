package com.appname.dqra.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;

/**
 * Helper class for handling Base64 images in the admin interface
 */
public class Base64ImageHelper {

    /**
     * Convert Base64 string to Bitmap
     * @param base64String The Base64 encoded image string
     * @return Bitmap object or null if conversion fails
     */
    public static Bitmap base64ToBitmap(String base64String) {
        try {
            byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception e) {
            Log.e("Base64ImageHelper", "Failed to decode Base64 image: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Load Base64 image into ImageView
     * @param imageView The ImageView to load the image into
     * @param base64String The Base64 encoded image string
     * @return true if successful, false if failed
     */
    public static boolean loadBase64IntoImageView(ImageView imageView, String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return false;
        }

        try {
            Bitmap bitmap = base64ToBitmap(base64String);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                return true;
            }
        } catch (Exception e) {
            Log.e("Base64ImageHelper", "Failed to load Base64 into ImageView: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Get the approximate size of a Base64 string in KB
     * @param base64String The Base64 encoded string
     * @return Size in KB
     */
    public static double getBase64SizeKB(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return 0;
        }
        // Base64 encoding increases size by ~33%, so actual size is ~75% of string length
        return (base64String.length() * 0.75) / 1024.0;
    }

    /**
     * Check if Base64 string represents a valid image
     * @param base64String The Base64 encoded string
     * @return true if it's a valid image, false otherwise
     */
    public static boolean isValidBase64Image(String base64String) {
        try {
            Bitmap bitmap = base64ToBitmap(base64String);
            return bitmap != null;
        } catch (Exception e) {
            return false;
        }
    }
}