# ObjectDetector Android App - Implementation Plan

## Overview
Android app in Kotlin using CameraX + Google ML Kit Object Detection that:
1. Detects objects in real-time camera feed
2. Lets the user tap a detected object and assign a custom label
3. Stores custom labels locally (Room DB) with visual features
4. Re-recognizes the specific object and shows the custom label

## Architecture

### Re-recognition Strategy
When a user labels an object:
- Crop the detected region from the camera frame
- Compute a **color histogram** (feature vector) from the cropped image
- Store: ML Kit category + custom label + feature vector in Room DB

When an object appears again:
- ML Kit detects it and provides a category label
- Crop the region, compute color histogram
- Compare against stored objects with matching category using cosine similarity
- If similarity > threshold → show the custom label

### Project Structure
```
app/src/main/java/com/objectdetector/
├── MainActivity.kt                 // CameraX setup, permissions, UI coordination
├── ObjectDetectorAnalyzer.kt       // ML Kit ImageAnalysis.Analyzer
├── ObjectOverlayView.kt            // Custom View drawing bounding boxes + labels
├── LabelDialogFragment.kt          // Dialog for entering custom label
├── data/
│   ├── AppDatabase.kt              // Room database singleton
│   ├── CustomLabel.kt              // Room Entity (id, mlKitCategory, customName, featureVector, timestamp)
│   └── CustomLabelDao.kt           // Room DAO (insert, query by category, delete)
└── util/
    └── FeatureExtractor.kt         // Color histogram computation + cosine similarity

app/src/main/res/
├── layout/
│   ├── activity_main.xml           // PreviewView + OverlayView + bottom info bar
│   └── dialog_label.xml            // EditText for custom label input
└── values/
    ├── strings.xml
    ├── colors.xml
    └── themes.xml
```

### Dependencies
| Library | Version | Purpose |
|---------|---------|---------|
| CameraX (core, camera2, lifecycle, view) | 1.4.0 | Camera preview & image analysis |
| ML Kit Object Detection | 17.0.2 | Real-time object detection |
| Room (runtime, ktx, compiler) | 2.6.1 | Local database for custom labels |
| Kotlin Coroutines | 1.7.3 | Async operations |
| Material Design | 1.12.0 | UI components & dialogs |
| Gson | 2.10.1 | Serialize feature vectors to JSON for Room storage |

### SDK/Build Config
- compileSdk: 35
- minSdk: 24
- targetSdk: 35
- Kotlin: 1.9.22
- AGP: 8.7.0
- Gradle: 8.9
- Java 17

## Implementation Steps

### Step 1: Project Scaffolding
Create the full Android project structure:
- Root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- App `build.gradle.kts` with all dependencies
- `AndroidManifest.xml` with camera permission
- Gradle wrapper files
- Basic resource files (strings, colors, themes)

### Step 2: Room Database Layer
- `CustomLabel` entity with fields: id, mlKitCategory, customName, featureVector (stored as JSON string), createdAt
- `CustomLabelDao` with: insert, getAllByCategory, getAll, deleteById
- `AppDatabase` Room database class with singleton pattern

### Step 3: Feature Extraction Utility
- `FeatureExtractor.kt`:
  - `extractColorHistogram(bitmap: Bitmap): FloatArray` — 64-bin RGB histogram (4x4x4 quantization)
  - `cosineSimilarity(a: FloatArray, b: FloatArray): Float` — similarity score
  - `serializeFeatures(features: FloatArray): String` — to JSON
  - `deserializeFeatures(json: String): FloatArray` — from JSON

### Step 4: ML Kit Object Detection Analyzer
- `ObjectDetectorAnalyzer` implements `ImageAnalysis.Analyzer`
- Configures ML Kit in STREAM_MODE with classification enabled
- Converts CameraX ImageProxy → InputImage
- Returns list of `DetectedObjectInfo` (boundingBox, labels, trackingId, croppedBitmap)
- Callback interface to pass results to the Activity

### Step 5: Overlay View
- `ObjectOverlayView` custom View
- Draws bounding boxes with labels on top of camera preview
- Handles coordinate transformation (image → preview coordinates)
- Touch detection: determine which bounding box was tapped
- Shows custom label (green box) vs generic label (white box)

### Step 6: Main Activity
- Request camera permission
- Set up CameraX: Preview + ImageAnalysis use cases
- Bind to lifecycle
- Receive detection results → update overlay
- For each detection, query Room DB for matching custom label (by category + feature similarity)
- On bounding box tap → show label dialog

### Step 7: Label Dialog
- Material AlertDialog with EditText
- Shows the ML Kit detected category as hint
- On confirm: crop detected region, extract features, save to Room
- On delete: remove custom label from Room

### Step 8: Re-recognition Logic (in MainActivity)
For each frame's detected objects:
1. Get ML Kit category label
2. Query stored custom labels with same category
3. Extract features from current detection's cropped image
4. Compare with each stored label's features via cosine similarity
5. If best match > 0.85 threshold → display custom label
6. Cache results using ML Kit tracking ID to avoid redundant computation

## Files to Create (in order)
1. `settings.gradle.kts` (root)
2. `build.gradle.kts` (root)
3. `gradle.properties`
4. `gradle/wrapper/gradle-wrapper.properties`
5. `app/build.gradle.kts`
6. `app/src/main/AndroidManifest.xml`
7. `app/src/main/res/values/strings.xml`
8. `app/src/main/res/values/colors.xml`
9. `app/src/main/res/values/themes.xml`
10. `app/src/main/res/layout/activity_main.xml`
11. `app/src/main/res/layout/dialog_label.xml`
12. `app/src/main/java/com/objectdetector/data/CustomLabel.kt`
13. `app/src/main/java/com/objectdetector/data/CustomLabelDao.kt`
14. `app/src/main/java/com/objectdetector/data/AppDatabase.kt`
15. `app/src/main/java/com/objectdetector/util/FeatureExtractor.kt`
16. `app/src/main/java/com/objectdetector/ObjectDetectorAnalyzer.kt`
17. `app/src/main/java/com/objectdetector/ObjectOverlayView.kt`
18. `app/src/main/java/com/objectdetector/LabelDialogFragment.kt`
19. `app/src/main/java/com/objectdetector/MainActivity.kt`
