package com.homeobjectcontroller.util

import android.graphics.Bitmap
import com.google.gson.Gson
import kotlin.math.sqrt

object FeatureExtractor {

    private const val BINS_PER_CHANNEL = 4
    private const val TOTAL_BINS = BINS_PER_CHANNEL * BINS_PER_CHANNEL * BINS_PER_CHANNEL // 64
    private val gson = Gson()

    /**
     * Extracts a 64-bin RGB color histogram from the bitmap.
     * Each channel is quantized into 4 bins (4x4x4 = 64 total bins).
     * The histogram is normalized to sum to 1.
     */
    fun extractColorHistogram(bitmap: Bitmap): FloatArray {
        val histogram = FloatArray(TOTAL_BINS)
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val binSize = 256 / BINS_PER_CHANNEL

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val rBin = (r / binSize).coerceAtMost(BINS_PER_CHANNEL - 1)
            val gBin = (g / binSize).coerceAtMost(BINS_PER_CHANNEL - 1)
            val bBin = (b / binSize).coerceAtMost(BINS_PER_CHANNEL - 1)

            val index = rBin * BINS_PER_CHANNEL * BINS_PER_CHANNEL + gBin * BINS_PER_CHANNEL + bBin
            histogram[index]++
        }

        // Normalize
        val total = pixels.size.toFloat()
        if (total > 0) {
            for (i in histogram.indices) {
                histogram[i] /= total
            }
        }

        return histogram
    }

    /**
     * Computes cosine similarity between two feature vectors.
     * Returns a value between 0 and 1 (higher = more similar).
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }

    fun serializeFeatures(features: FloatArray): String {
        return gson.toJson(features)
    }

    fun deserializeFeatures(json: String): FloatArray {
        return gson.fromJson(json, FloatArray::class.java)
    }
}
