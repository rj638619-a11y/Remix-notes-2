package com.example.util

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

class RealFaceAnalyzer(
    private val isFrontCamera: Boolean = true,
    private val onFaceDetected: (List<DetectedFaceData>) -> Unit,
    private val onError: (Exception) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        detector = FaceDetection.getClient(options)
    }

    private var isProcessing = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // Calculate dimensions after rotation
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imageWidth = if (isRotated) imageProxy.height else imageProxy.width
        val imageHeight = if (isRotated) imageProxy.width else imageProxy.height

        detector.process(image)
            .addOnSuccessListener { faces ->
                val detectedList = faces.map { face ->
                    transformFace(face, imageWidth.toFloat(), imageHeight.toFloat())
                }
                onFaceDetected(detectedList)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    private fun transformFace(face: Face, imgW: Float, imgH: Float): DetectedFaceData {
        val rawBounds = face.boundingBox

        // Transform bounding box to normalized 0..1 coordinates
        val leftNorm = if (isFrontCamera) {
            (1f - (rawBounds.right.toFloat() / imgW)).coerceIn(0f, 1f)
        } else {
            (rawBounds.left.toFloat() / imgW).coerceIn(0f, 1f)
        }

        val rightNorm = if (isFrontCamera) {
            (1f - (rawBounds.left.toFloat() / imgW)).coerceIn(0f, 1f)
        } else {
            (rawBounds.right.toFloat() / imgW).coerceIn(0f, 1f)
        }

        val topNorm = (rawBounds.top.toFloat() / imgH).coerceIn(0f, 1f)
        val bottomNorm = (rawBounds.bottom.toFloat() / imgH).coerceIn(0f, 1f)

        val normalizedBounds = RectF(
            minOf(leftNorm, rightNorm),
            minOf(topNorm, bottomNorm),
            maxOf(leftNorm, rightNorm),
            maxOf(topNorm, bottomNorm)
        )

        fun transformPoint(p: PointF?): PointF? {
            if (p == null) return null
            val xNorm = if (isFrontCamera) 1f - (p.x / imgW) else (p.x / imgW)
            val yNorm = p.y / imgH
            return PointF(xNorm.coerceIn(0f, 1f), yNorm.coerceIn(0f, 1f))
        }

        val lEye = transformPoint(face.getLandmark(FaceLandmark.LEFT_EYE)?.position)
        val rEye = transformPoint(face.getLandmark(FaceLandmark.RIGHT_EYE)?.position)
        val nose = transformPoint(face.getLandmark(FaceLandmark.NOSE_BASE)?.position)
        val mouthL = transformPoint(face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position)
        val mouthR = transformPoint(face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position)
        val mouthB = transformPoint(face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position)
        val leftCheek = transformPoint(face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position)
        val rightCheek = transformPoint(face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position)
        val leftEar = transformPoint(face.getLandmark(FaceLandmark.LEFT_EAR)?.position)
        val rightEar = transformPoint(face.getLandmark(FaceLandmark.RIGHT_EAR)?.position)

        // Extract contours for real-time biometric wireframe
        val contourMap = mutableMapOf<String, List<PointF>>()
        val contourTypes = listOf(
            FaceContour.FACE to "face",
            FaceContour.LEFT_EYEBROW_TOP to "left_eyebrow",
            FaceContour.RIGHT_EYEBROW_TOP to "right_eyebrow",
            FaceContour.LEFT_EYE to "left_eye",
            FaceContour.RIGHT_EYE to "right_eye",
            FaceContour.UPPER_LIP_TOP to "upper_lip",
            FaceContour.LOWER_LIP_BOTTOM to "lower_lip",
            FaceContour.NOSE_BRIDGE to "nose_bridge"
        )

        for ((type, name) in contourTypes) {
            val contour = face.getContour(type)
            if (contour != null && contour.points.isNotEmpty()) {
                val points = contour.points.mapNotNull { p ->
                    transformPoint(PointF(p.x, p.y))
                }
                if (points.isNotEmpty()) {
                    contourMap[name] = points
                }
            }
        }

        return DetectedFaceData(
            bounds = normalizedBounds,
            originalBounds = rawBounds,
            leftEye = lEye,
            rightEye = rEye,
            noseBase = nose,
            mouthLeft = mouthL,
            mouthRight = mouthR,
            mouthBottom = mouthB,
            leftCheek = leftCheek,
            rightCheek = rightCheek,
            leftEar = leftEar,
            rightEar = rightEar,
            contours = contourMap,
            smilingProbability = face.smilingProbability,
            leftEyeOpenProbability = face.leftEyeOpenProbability,
            rightEyeOpenProbability = face.rightEyeOpenProbability,
            headEulerAngleX = face.headEulerAngleX,
            headEulerAngleY = face.headEulerAngleY,
            headEulerAngleZ = face.headEulerAngleZ,
            trackingId = face.trackingId
        )
    }

    fun close() {
        detector.close()
    }
}
