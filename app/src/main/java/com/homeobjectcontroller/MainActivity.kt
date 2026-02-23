package com.homeobjectcontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.homeobjectcontroller.data.AppDatabase
import com.homeobjectcontroller.data.CustomLabel
import com.homeobjectcontroller.util.FeatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: ObjectOverlayView
    private lateinit var infoBar: TextView

    private lateinit var cameraExecutor: ExecutorService
    private var analyzer: ObjectDetectorAnalyzer? = null

    private val database by lazy { AppDatabase.getInstance(this) }
    private val dao by lazy { database.customLabelDao() }

    // Cache: trackingId -> (customLabelName, customLabelId) to avoid redundant DB queries per frame
    private val recognitionCache = mutableMapOf<Int, Pair<String, Long>?>()

    private val SIMILARITY_THRESHOLD = 0.85f

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        infoBar = findViewById(R.id.infoBar)

        cameraExecutor = Executors.newSingleThreadExecutor()

        overlayView.setOnObjectTappedListener { obj ->
            showLabelDialog(obj)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            analyzer = ObjectDetectorAnalyzer { detections, imgWidth, imgHeight ->
                runOnUiThread {
                    processDetections(detections, imgWidth, imgHeight)
                }
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer!!) }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processDetections(detections: List<DetectedObjectInfo>, imgWidth: Int, imgHeight: Int) {
        if (detections.isEmpty()) {
            infoBar.setText(R.string.no_objects_detected)
            overlayView.clear()
            return
        }

        infoBar.text = getString(R.string.objects_detected, detections.size)

        lifecycleScope.launch {
            val overlayObjects = detections.map { detection ->
                val category = detection.labels.firstOrNull() ?: "Unknown"
                val trackingId = detection.trackingId

                // Check cache first
                val cached = trackingId?.let { recognitionCache[it] }
                if (cached != null) {
                    OverlayObject(
                        boundingBox = detection.boundingBox,
                        label = cached.first,
                        isCustomLabel = true,
                        trackingId = trackingId,
                        detectedInfo = detection
                    )
                } else {
                    // Try to re-recognize from DB
                    val match = findMatchingCustomLabel(detection, category)
                    if (match != null) {
                        trackingId?.let { recognitionCache[it] = Pair(match.customName, match.id) }
                        OverlayObject(
                            boundingBox = detection.boundingBox,
                            label = match.customName,
                            isCustomLabel = true,
                            trackingId = trackingId,
                            detectedInfo = detection
                        )
                    } else {
                        OverlayObject(
                            boundingBox = detection.boundingBox,
                            label = category,
                            isCustomLabel = false,
                            trackingId = trackingId,
                            detectedInfo = detection
                        )
                    }
                }
            }

            overlayView.updateObjects(overlayObjects, imgWidth, imgHeight)
        }
    }

    private suspend fun findMatchingCustomLabel(
        detection: DetectedObjectInfo,
        category: String
    ): CustomLabel? = withContext(Dispatchers.IO) {
        val bitmap = detection.croppedBitmap ?: return@withContext null
        val features = FeatureExtractor.extractColorHistogram(bitmap)
        val storedLabels = dao.getAllByCategory(category)

        var bestMatch: CustomLabel? = null
        var bestSimilarity = SIMILARITY_THRESHOLD

        for (stored in storedLabels) {
            val storedFeatures = FeatureExtractor.deserializeFeatures(stored.featureVector)
            val similarity = FeatureExtractor.cosineSimilarity(features, storedFeatures)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = stored
            }
        }

        bestMatch
    }

    private fun showLabelDialog(obj: OverlayObject) {
        val category = obj.detectedInfo.labels.firstOrNull() ?: "Unknown"
        val existingLabel = if (obj.isCustomLabel) obj.label else null

        // Find existing DB id if this is a custom-labeled object
        val existingId = obj.trackingId?.let { recognitionCache[it]?.second }

        val dialog = LabelDialogFragment.newInstance(
            category = category,
            existingLabel = existingLabel,
            onSave = { newLabel ->
                saveCustomLabel(obj, category, newLabel, existingId)
            },
            onDelete = {
                existingId?.let { deleteCustomLabel(it, obj.trackingId) }
            }
        )
        dialog.show(supportFragmentManager, "label_dialog")
    }

    private fun saveCustomLabel(obj: OverlayObject, category: String, customName: String, existingId: Long?) {
        val bitmap = obj.detectedInfo.croppedBitmap ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val features = FeatureExtractor.extractColorHistogram(bitmap)
            val featureJson = FeatureExtractor.serializeFeatures(features)

            val label = CustomLabel(
                id = existingId ?: 0,
                mlKitCategory = category,
                customName = customName,
                featureVector = featureJson
            )
            val id = dao.insert(label)

            // Update cache
            obj.trackingId?.let { recognitionCache[it] = Pair(customName, id) }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.custom_label_saved, customName),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun deleteCustomLabel(labelId: Long, trackingId: Int?) {
        lifecycleScope.launch(Dispatchers.IO) {
            dao.deleteById(labelId)
            trackingId?.let { recognitionCache.remove(it) }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    R.string.custom_label_deleted,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        analyzer?.close()
    }
}
