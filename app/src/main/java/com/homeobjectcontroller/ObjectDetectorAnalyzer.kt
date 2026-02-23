package com.homeobjectcontroller

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.ByteArrayOutputStream

data class DetectedObjectInfo(
    val boundingBox: Rect,
    val labels: List<String>,
    val trackingId: Int?,
    val croppedBitmap: Bitmap?
)

class ObjectDetectorAnalyzer(
    private val onDetectionResult: (List<DetectedObjectInfo>, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector: ObjectDetector

    init {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .build()
        detector = ObjectDetection.getClient(options)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        // Convert to bitmap for cropping before closing the proxy
        val fullBitmap = mediaImageToBitmap(mediaImage, rotation)

        detector.process(inputImage)
            .addOnSuccessListener { detectedObjects ->
                val results = detectedObjects.map { obj ->
                    val labels = obj.labels.map { it.text }.ifEmpty { listOf("Unknown") }
                    val cropped = fullBitmap?.let { bmp ->
                        cropBitmap(bmp, obj.boundingBox)
                    }
                    DetectedObjectInfo(
                        boundingBox = obj.boundingBox,
                        labels = labels,
                        trackingId = obj.trackingId,
                        croppedBitmap = cropped
                    )
                }
                onDetectionResult(results, inputImage.width, inputImage.height)
            }
            .addOnFailureListener {
                onDetectionResult(emptyList(), inputImage.width, inputImage.height)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun mediaImageToBitmap(image: Image, rotationDegrees: Int): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
            val bytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap? {
        return try {
            val left = rect.left.coerceAtLeast(0)
            val top = rect.top.coerceAtLeast(0)
            val right = rect.right.coerceAtMost(bitmap.width)
            val bottom = rect.bottom.coerceAtMost(bitmap.height)
            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        detector.close()
    }
}
