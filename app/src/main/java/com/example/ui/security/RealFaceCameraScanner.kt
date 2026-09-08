package com.example.ui.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.GlassTheme
import com.example.util.DetectedFaceData
import com.example.util.FaceSignature
import com.example.util.RealFaceAnalyzer
import com.example.util.VibrationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs

sealed interface RealFaceScannerMode {
    data class Unlock(val enrolledSignature: FaceSignature?, val onSuccess: () -> Unit) : RealFaceScannerMode
    data class Enroll(val onEnrolled: (FaceSignature) -> Unit) : RealFaceScannerMode
    object Diagnostic : RealFaceScannerMode
}

@Composable
fun RealFaceCameraScanner(
    mode: RealFaceScannerMode,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = GlassTheme.colors
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            VibrationHelper.error(context)
        }
    }

    var isFrontCamera by remember { mutableStateOf(true) }
    var detectedFaces by remember { mutableStateOf<List<DetectedFaceData>>(emptyList()) }
    var scanStatus by remember { mutableStateOf("SCANNING") } // "SCANNING", "VERIFYING", "SUCCESS", "FAILED"
    var statusText by remember { mutableStateOf("Position face inside the target frame") }
    var consecutiveValidFrames by remember { mutableIntStateOf(0) }
    var matchScore by remember { mutableFloatStateOf(0f) }

    // Laser Animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser_anim")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    // Primary detected face
    val primaryFace = detectedFaces.firstOrNull()

    // Real-time verification / enrollment logic
    LaunchedEffect(primaryFace, scanStatus) {
        if (scanStatus == "SUCCESS") return@LaunchedEffect

        if (primaryFace == null) {
            consecutiveValidFrames = 0
            if (scanStatus != "SCANNING") scanStatus = "SCANNING"
            statusText = "Searching for face..."
            return@LaunchedEffect
        }

        if (detectedFaces.size > 1) {
            consecutiveValidFrames = 0
            statusText = "Multiple faces detected — please center only one face"
            return@LaunchedEffect
        }

        // Check if face is frontal and reasonably sized
        val bounds = primaryFace.bounds
        val faceWidth = bounds.width()
        val isFaceCentered = bounds.centerX() in 0.25f..0.75f && bounds.centerY() in 0.20f..0.80f

        if (!isFaceCentered) {
            statusText = "Center your face in the oval"
            consecutiveValidFrames = 0
            return@LaunchedEffect
        }

        if (!primaryFace.isFrontal) {
            val yaw = primaryFace.headEulerAngleY
            val pitch = primaryFace.headEulerAngleX
            statusText = if (abs(yaw) > 18f) "Please look directly at camera" else "Keep head level"
            consecutiveValidFrames = 0
            return@LaunchedEffect
        }

        if (faceWidth < 0.22f) {
            statusText = "Move a little closer"
            consecutiveValidFrames = 0
            return@LaunchedEffect
        }

        // Face is well-positioned!
        consecutiveValidFrames++

        when (mode) {
            is RealFaceScannerMode.Unlock -> {
                val enrolled = mode.enrolledSignature
                if (enrolled == null) {
                    consecutiveValidFrames = 0
                    statusText = "Security Error: No enrolled face profile found."
                } else {
                    val currentSig = primaryFace.computeSignature()
                    if (currentSig != null) {
                        val score = enrolled.matchScore(currentSig)
                        matchScore = score

                        val matchThreshold = 0.75f
                        if (score >= matchThreshold) {
                            statusText = "Biometric Match: ${(score * 100).toInt()}% • Hold steady"
                            if (consecutiveValidFrames >= 3) {
                                scanStatus = "SUCCESS"
                                statusText = "Face ID Verified!"
                                VibrationHelper.success(context)
                                scope.launch {
                                    delay(450)
                                    mode.onSuccess()
                                }
                            }
                        } else {
                            consecutiveValidFrames = 0
                            statusText = "Face not recognized (${(score * 100).toInt()}% match)"
                        }
                    } else {
                        consecutiveValidFrames = 0
                        statusText = "Aligning facial features... Look directly at the camera"
                    }
                }
            }

            is RealFaceScannerMode.Enroll -> {
                val currentSig = primaryFace.computeSignature()
                if (currentSig != null) {
                    statusText = "Scanning facial geometry (${consecutiveValidFrames}/5)..."
                    VibrationHelper.vibrate(context, 8)
                    if (consecutiveValidFrames >= 5) {
                        scanStatus = "SUCCESS"
                        statusText = "Face Profile Enrolled!"
                        VibrationHelper.success(context)
                        scope.launch {
                            delay(500)
                            mode.onEnrolled(currentSig)
                        }
                    }
                } else {
                    statusText = "Aligning facial features..."
                }
            }

            is RealFaceScannerMode.Diagnostic -> {
                val smile = ((primaryFace.smilingProbability ?: 0f) * 100).toInt()
                val leftEye = ((primaryFace.leftEyeOpenProbability ?: 0.5f) * 100).toInt()
                val rightEye = ((primaryFace.rightEyeOpenProbability ?: 0.5f) * 100).toInt()
                statusText = "Live Detection: Smile ${smile}% • Eyes L${leftEye}% R${rightEye}% • Yaw ${primaryFace.headEulerAngleY.toInt()}°"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasCameraPermission) {
            // Camera Permission Request View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f))
                        .border(1.5.dp, colors.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Camera Required",
                        tint = colors.accent,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Camera Access Required",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Real-time face detection analyzes your live camera feed to verify facial biometric signatures on-device.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Text("Grant Camera Permission", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Text("Cancel / Use PIN")
                }
            }
        } else {
            // Live Camera View & Biometric Canvas Overlay
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
            var faceAnalyzer by remember { mutableStateOf<RealFaceAnalyzer?>(null) }

            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = if (isFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        val analyzer = RealFaceAnalyzer(
                            isFrontCamera = isFrontCamera,
                            onFaceDetected = { faces ->
                                detectedFaces = faces
                            },
                            onError = { _ -> }
                        )
                        faceAnalyzer = analyzer

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, analyzer)
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            DisposableEffect(Unit) {
                onDispose {
                    faceAnalyzer?.close()
                    cameraExecutor.shutdown()
                }
            }

            // Real-Time Biometric HUD Canvas Overlay
            val primaryColor = when (scanStatus) {
                "SUCCESS" -> Color(0xFF10B981)
                "FAILED" -> Color(0xFFEF4444)
                else -> if (primaryFace != null && primaryFace.isFrontal) Color(0xFF06B6D4) else Color(0xFF38BDF8)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 1. Dark Vignette Outer Mask with Target Oval cutout
                val ovalWidth = w * 0.76f
                val ovalHeight = h * 0.48f
                val ovalLeft = (w - ovalWidth) / 2f
                val ovalTop = h * 0.16f

                // Draw alignment target oval
                drawRoundRect(
                    color = primaryColor.copy(alpha = if (primaryFace != null) 0.8f else 0.4f),
                    topLeft = Offset(ovalLeft, ovalTop),
                    size = Size(ovalWidth, ovalHeight),
                    cornerRadius = CornerRadius(ovalWidth / 2f, ovalHeight / 2f),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
                    )
                )

                // 2. Real-Time Tracked Face Bounding Box
                primaryFace?.let { face ->
                    val fb = face.bounds
                    val boxLeft = fb.left * w
                    val boxTop = fb.top * h
                    val boxW = (fb.right - fb.left) * w
                    val boxH = (fb.bottom - fb.top) * h

                    // Glowing bounding box outline
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.35f),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // 4 Glowing Corner Brackets
                    val cornerSize = 28.dp.toPx()
                    val strokeW = 4.dp.toPx()

                    // Top-Left
                    drawLine(primaryColor, Offset(boxLeft, boxTop), Offset(boxLeft + cornerSize, boxTop), strokeW, StrokeCap.Round)
                    drawLine(primaryColor, Offset(boxLeft, boxTop), Offset(boxLeft, boxTop + cornerSize), strokeW, StrokeCap.Round)

                    // Top-Right
                    drawLine(primaryColor, Offset(boxLeft + boxW, boxTop), Offset(boxLeft + boxW - cornerSize, boxTop), strokeW, StrokeCap.Round)
                    drawLine(primaryColor, Offset(boxLeft + boxW, boxTop), Offset(boxLeft + boxW, boxTop + cornerSize), strokeW, StrokeCap.Round)

                    // Bottom-Left
                    drawLine(primaryColor, Offset(boxLeft, boxTop + boxH), Offset(boxLeft + cornerSize, boxTop + boxH), strokeW, StrokeCap.Round)
                    drawLine(primaryColor, Offset(boxLeft, boxTop + boxH), Offset(boxLeft, boxTop + boxH - cornerSize), strokeW, StrokeCap.Round)

                    // Bottom-Right
                    drawLine(primaryColor, Offset(boxLeft + boxW, boxTop + boxH), Offset(boxLeft + boxW - cornerSize, boxTop + boxH), strokeW, StrokeCap.Round)
                    drawLine(primaryColor, Offset(boxLeft + boxW, boxTop + boxH), Offset(boxLeft + boxW, boxTop + boxH - cornerSize), strokeW, StrokeCap.Round)

                    // 3. Real-Time Facial Landmarks Dots
                    fun drawLandmark(pt: PointF?, color: Color, r: Float = 4.5.dp.toPx()) {
                        if (pt != null) {
                            val px = pt.x * w
                            val py = pt.y * h
                            drawCircle(color.copy(alpha = 0.3f), r * 2.2f, Offset(px, py))
                            drawCircle(color, r, Offset(px, py))
                            drawCircle(Color.White, r * 0.45f, Offset(px, py))
                        }
                    }

                    drawLandmark(face.leftEye, primaryColor)
                    drawLandmark(face.rightEye, primaryColor)
                    drawLandmark(face.noseBase, primaryColor)
                    drawLandmark(face.mouthLeft, primaryColor)
                    drawLandmark(face.mouthRight, primaryColor)
                    drawLandmark(face.mouthBottom, primaryColor)

                    // 4. Real-Time Facial Contour Wireframe
                    for ((_, points) in face.contours) {
                        if (points.size >= 2) {
                            val path = Path()
                            val first = points.first()
                            path.moveTo(first.x * w, first.y * h)
                            for (i in 1 until points.size) {
                                val p = points[i]
                                path.lineTo(p.x * w, p.y * h)
                            }
                            drawPath(
                                path = path,
                                color = primaryColor.copy(alpha = 0.45f),
                                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // 5. Animated Laser Scan Bar inside face bounding box
                    if (scanStatus != "SUCCESS") {
                        val laserY = boxTop + (boxH * laserProgress)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primaryColor,
                                    Color.White,
                                    primaryColor,
                                    Color.Transparent
                                )
                            ),
                            start = Offset(boxLeft, laserY),
                            end = Offset(boxLeft + boxW, laserY),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Foreground UI Overlay (Header, Live Telemetry Bar, Bottom Actions)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Tag Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (primaryFace != null) Color(0xFF10B981) else Color(0xFFEAB308))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (mode) {
                                    is RealFaceScannerMode.Unlock -> "FACE UNLOCK"
                                    is RealFaceScannerMode.Enroll -> "FACE ENROLLMENT"
                                    is RealFaceScannerMode.Diagnostic -> "ACCURATE FACE DETECTION"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    // Camera Switch & Close Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable {
                                isFrontCamera = !isFrontCamera
                                VibrationHelper.click(context)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable { onCancel() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Bottom Telemetry & Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Live Status Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.2.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (scanStatus == "SUCCESS") {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Text(
                                    text = statusText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Real-time Facial Metric Gauges
                            if (primaryFace != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    val leftEye = ((primaryFace.leftEyeOpenProbability ?: 0.5f) * 100).toInt()
                                    val rightEye = ((primaryFace.rightEyeOpenProbability ?: 0.5f) * 100).toInt()
                                    val smile = ((primaryFace.smilingProbability ?: 0f) * 100).toInt()

                                    MetricPill(label = "Eyes Open", value = "${leftEye}% / ${rightEye}%")
                                    MetricPill(label = "Head Pose", value = "${primaryFace.headEulerAngleY.toInt()}°")
                                    MetricPill(label = "Smile", value = "${smile}%")
                                    if (matchScore > 0f) {
                                        MetricPill(label = "Match", value = "${(matchScore * 100).toInt()}%")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Fallback to PIN button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onCancel() }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pin,
                            contentDescription = "PIN",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Use PIN Code Instead",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color(0xFF38BDF8),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
