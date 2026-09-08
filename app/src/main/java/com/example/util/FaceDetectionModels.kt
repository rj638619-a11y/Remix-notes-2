package com.example.util

import android.graphics.PointF
import android.graphics.RectF
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Detailed real-time data extracted from a detected face via Google ML Kit Face Detection.
 */
data class DetectedFaceData(
    val bounds: RectF, // Coordinates normalized to 0..1 in view space
    val originalBounds: android.graphics.Rect,
    val leftEye: PointF? = null,
    val rightEye: PointF? = null,
    val noseBase: PointF? = null,
    val mouthLeft: PointF? = null,
    val mouthRight: PointF? = null,
    val mouthBottom: PointF? = null,
    val leftCheek: PointF? = null,
    val rightCheek: PointF? = null,
    val leftEar: PointF? = null,
    val rightEar: PointF? = null,
    val contours: Map<String, List<PointF>> = emptyMap(),
    val smilingProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val headEulerAngleX: Float = 0f, // Pitch (nod up/down)
    val headEulerAngleY: Float = 0f, // Yaw (turn left/right)
    val headEulerAngleZ: Float = 0f, // Roll (tilt left/right)
    val trackingId: Int? = null,
    val isFrontal: Boolean = abs(headEulerAngleY) < 18f && abs(headEulerAngleX) < 20f && abs(headEulerAngleZ) < 16f,
    val isEyesOpen: Boolean = (leftEyeOpenProbability ?: 0.5f) > 0.45f && (rightEyeOpenProbability ?: 0.5f) > 0.45f,
    val confidence: Float = 0.98f
) {
    /**
     * Compute a scale-invariant geometric signature from key facial landmarks.
     */
    fun computeSignature(): FaceSignature? {
        val lEye = leftEye ?: return null
        val rEye = rightEye ?: return null
        val nose = noseBase ?: return null
        val mMouth = if (mouthLeft != null && mouthRight != null) {
            PointF((mouthLeft.x + mouthRight.x) / 2f, (mouthLeft.y + mouthRight.y) / 2f)
        } else mouthBottom ?: return null

        val eyeDist = dist(lEye, rEye)
        if (eyeDist <= 0.001f) return null

        val eyeMid = PointF((lEye.x + rEye.x) / 2f, (lEye.y + rEye.y) / 2f)
        val eyeToNose = dist(eyeMid, nose)
        val noseToMouth = dist(nose, mMouth)
        val mouthWidth = if (mouthLeft != null && mouthRight != null) dist(mouthLeft, mouthRight) else eyeDist * 0.7f

        val faceW = bounds.width().coerceAtLeast(0.01f)
        val faceH = bounds.height().coerceAtLeast(0.01f)

        return FaceSignature(
            eyeDistanceRatio = (eyeDist / faceW).coerceIn(0.1f, 1.0f),
            eyeToNoseRatio = (eyeToNose / eyeDist).coerceIn(0.1f, 3.0f),
            noseToMouthRatio = (noseToMouth / eyeDist).coerceIn(0.1f, 3.0f),
            mouthWidthRatio = (mouthWidth / eyeDist).coerceIn(0.1f, 3.0f),
            faceAspectRatio = (faceW / faceH).coerceIn(0.3f, 2.0f)
        )
    }

    private fun dist(p1: PointF, p2: PointF): Float {
        return hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()
    }
}

/**
 * Normalized geometric signature stored for face enrollment and matching.
 */
data class FaceSignature(
    val eyeDistanceRatio: Float,
    val eyeToNoseRatio: Float,
    val noseToMouthRatio: Float,
    val mouthWidthRatio: Float,
    val faceAspectRatio: Float
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("eyeDist", eyeDistanceRatio.toDouble())
        json.put("eyeNose", eyeToNoseRatio.toDouble())
        json.put("noseMouth", noseToMouthRatio.toDouble())
        json.put("mouthW", mouthWidthRatio.toDouble())
        json.put("aspect", faceAspectRatio.toDouble())
        return json.toString()
    }

    /**
     * Compare against another signature and compute a match similarity score (0.0 to 1.0).
     */
    fun matchScore(other: FaceSignature): Float {
        // Significantly reduced denominators (lower tolerance windows) to prevent unauthorized faces from matching.
        val d1 = abs(eyeDistanceRatio - other.eyeDistanceRatio) / 0.06f
        val d2 = abs(eyeToNoseRatio - other.eyeToNoseRatio) / 0.10f
        val d3 = abs(noseToMouthRatio - other.noseToMouthRatio) / 0.12f
        val d4 = abs(mouthWidthRatio - other.mouthWidthRatio) / 0.10f
        val d5 = abs(faceAspectRatio - other.faceAspectRatio) / 0.10f

        val totalDist = (d1 * 1.5f + d2 * 1.2f + d3 * 1.0f + d4 * 1.0f + d5 * 1.0f) / 5.7f
        val score = (1.0f - totalDist).coerceIn(0f, 1f)
        return score
    }

    companion object {
        fun fromJson(jsonStr: String): FaceSignature? {
            if (jsonStr.isBlank()) return null
            return try {
                val json = JSONObject(jsonStr)
                FaceSignature(
                    eyeDistanceRatio = json.optDouble("eyeDist", 0.4).toFloat(),
                    eyeToNoseRatio = json.optDouble("eyeNose", 0.6).toFloat(),
                    noseToMouthRatio = json.optDouble("noseMouth", 0.55).toFloat(),
                    mouthWidthRatio = json.optDouble("mouthW", 0.75).toFloat(),
                    faceAspectRatio = json.optDouble("aspect", 0.8).toFloat()
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
