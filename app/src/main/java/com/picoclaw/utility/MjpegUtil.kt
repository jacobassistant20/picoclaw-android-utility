package com.picoclaw.utility

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * MJPEG frame extraction utility.
 * Extracts JPEG frames from MJPEG multipart stream.
 */
object MjpegUtil {
    
    private val JPEG_START = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val JPEG_END = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    
    /**
     * Extract a JPEG frame from MJPEG data.
     * @param data Raw MJPEG bytes
     * @return JPEG frame bytes or null if not found
     */
    fun extractJpegFrame(data: ByteArray): ByteArray? {
        val startIndex = indexOf(data, JPEG_START)
        if (startIndex < 0) return null
        
        val searchFrom = if (startIndex > 0) startIndex else 0
        val endIndex = indexOf(data, JPEG_END, searchFrom)
        if (endIndex < 0) return null
        
        return data.copyOfRange(startIndex, endIndex + 2)
    }
    
    /**
     * Find byte pattern in data.
     */
    private fun indexOf(data: ByteArray, pattern: ByteArray, start: Int = 0): Int {
        outer@ for (i in start..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }
    
    /**
     * Encode JPEG bytes to Base64 string.
     */
    fun encodeToBase64(jpegData: ByteArray): String {
        return Base64.encodeToString(jpegData, Base64.NO_WRAP)
    }
    
    /**
     * Decode Base64 string to JPEG bytes.
     */
    fun decodeFromBase64(base64: String): ByteArray? {
        return try {
            Base64.decode(base64, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get image dimensions from JPEG data.
     */
    fun getImageDimensions(jpegData: ByteArray): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert JPEG bytes to Bitmap.
     */
    fun jpegToBitmap(jpegData: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Compress bitmap to JPEG bytes.
     */
    fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 80): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}