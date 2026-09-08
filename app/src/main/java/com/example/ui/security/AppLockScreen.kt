package com.example.ui.security

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import com.example.util.FaceSignature
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.GlassTheme
import com.example.util.BiometricHelper
import com.example.util.BiometricResult
import com.example.util.VibrationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface LockScreenMode {
    object UnlockApp : LockScreenMode
    data class UnlockNote(val noteTitle: String) : LockScreenMode
    data class SetupPin(val onPinCreated: (String) -> Unit) : LockScreenMode
    data class ChangePin(val currentPin: String, val onPinUpdated: (String) -> Unit) : LockScreenMode
}

@Composable
fun AppLockScreen(
    mode: LockScreenMode,
    correctPin: String,
    biometricEnabled: Boolean,
    faceSignature: String = "",
    faceEnrolled: Boolean = false,
    onSuccess: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = GlassTheme.colors
    val coroutineScope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInAppFaceScanner by remember { mutableStateOf(false) }

    // Multi-stage state for SetupPin & ChangePin
    var setupFirstPin by remember { mutableStateOf("") }
    var currentStage by remember {
        mutableStateOf(
            when (mode) {
                is LockScreenMode.SetupPin -> "ENTER_NEW"
                is LockScreenMode.ChangePin -> "ENTER_OLD"
                else -> "NORMAL"
            }
        )
    }

    val shakeOffset = remember { Animatable(0f) }

    fun triggerShake() {
        coroutineScope.launch {
            isError = true
            VibrationHelper.error(context)
            for (i in 0 until 3) {
                shakeOffset.animateTo(24f, tween(50))
                shakeOffset.animateTo(-24f, tween(50))
            }
            shakeOffset.animateTo(0f, tween(50))
            delay(1200)
            isError = false
            errorMessage = null
            enteredPin = ""
        }
    }

    fun launchFaceUnlock() {
        if (!biometricEnabled) return
        showInAppFaceScanner = true
    }

    // Auto-launch Face Unlock once when screen appears in unlock modes
    LaunchedEffect(mode, biometricEnabled) {
        if (biometricEnabled && (mode is LockScreenMode.UnlockApp || mode is LockScreenMode.UnlockNote)) {
            delay(200)
            launchFaceUnlock()
        }
    }

    fun handlePinComplete(pin: String) {
        when (mode) {
            is LockScreenMode.UnlockApp, is LockScreenMode.UnlockNote -> {
                if (pin == correctPin || correctPin.isBlank()) {
                    VibrationHelper.success(context)
                    onSuccess()
                } else {
                    errorMessage = "Incorrect PIN code"
                    triggerShake()
                }
            }
            is LockScreenMode.SetupPin -> {
                if (currentStage == "ENTER_NEW") {
                    setupFirstPin = pin
                    enteredPin = ""
                    currentStage = "CONFIRM_NEW"
                    VibrationHelper.click(context)
                } else {
                    if (pin == setupFirstPin) {
                        VibrationHelper.success(context)
                        mode.onPinCreated(pin)
                        onSuccess()
                    } else {
                        errorMessage = "PINs do not match. Start over."
                        currentStage = "ENTER_NEW"
                        setupFirstPin = ""
                        triggerShake()
                    }
                }
            }
            is LockScreenMode.ChangePin -> {
                when (currentStage) {
                    "ENTER_OLD" -> {
                        if (pin == mode.currentPin) {
                            currentStage = "ENTER_NEW"
                            enteredPin = ""
                            VibrationHelper.click(context)
                        } else {
                            errorMessage = "Current PIN is incorrect"
                            triggerShake()
                        }
                    }
                    "ENTER_NEW" -> {
                        setupFirstPin = pin
                        enteredPin = ""
                        currentStage = "CONFIRM_NEW"
                        VibrationHelper.click(context)
                    }
                    "CONFIRM_NEW" -> {
                        if (pin == setupFirstPin) {
                            VibrationHelper.success(context)
                            mode.onPinUpdated(pin)
                            onSuccess()
                        } else {
                            errorMessage = "PINs do not match. Try again."
                            currentStage = "ENTER_NEW"
                            setupFirstPin = ""
                            triggerShake()
                        }
                    }
                }
            }
        }
    }

    fun onDigitPress(digit: String) {
        if (enteredPin.length < 4 && !isError) {
            VibrationHelper.vibrate(context, 8)
            val updated = enteredPin + digit
            enteredPin = updated
            if (updated.length == 4) {
                coroutineScope.launch {
                    delay(120)
                    handlePinComplete(updated)
                }
            }
        }
    }

    fun onBackspacePress() {
        if (enteredPin.isNotEmpty() && !isError) {
            VibrationHelper.vibrate(context, 10)
            enteredPin = enteredPin.dropLast(1)
        }
    }

    fun onClearPress() {
        if (enteredPin.isNotEmpty() && !isError) {
            VibrationHelper.vibrate(context, 16)
            enteredPin = ""
        }
    }

    val titleText = when (mode) {
        is LockScreenMode.UnlockApp -> "Glass Notes Locked"
        is LockScreenMode.UnlockNote -> "Locked Note"
        is LockScreenMode.SetupPin -> if (currentStage == "ENTER_NEW") "Set Security PIN" else "Confirm Security PIN"
        is LockScreenMode.ChangePin -> when (currentStage) {
            "ENTER_OLD" -> "Enter Current PIN"
            "ENTER_NEW" -> "Enter New PIN"
            else -> "Confirm New PIN"
        }
    }

    val subtitleText = when (mode) {
        is LockScreenMode.UnlockApp -> if (biometricEnabled) "Enter your 4-digit PIN or use Face Unlock" else "Enter your 4-digit PIN"
        is LockScreenMode.UnlockNote -> "“${mode.noteTitle}” is protected"
        is LockScreenMode.SetupPin -> if (currentStage == "ENTER_NEW") "Choose a 4-digit passcode for your notes" else "Re-enter your 4-digit passcode to confirm"
        is LockScreenMode.ChangePin -> when (currentStage) {
            "ENTER_OLD" -> "Verify your existing passcode first"
            "ENTER_NEW" -> "Choose your new 4-digit passcode"
            else -> "Re-enter your new passcode to confirm"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Dismiss / Cancel at top right for dismissible modes
        if (onDismiss != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.field)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                // Glowing Icon Badge
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.accent.copy(alpha = 0.22f),
                                    colors.accent.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(1.5.dp, colors.accent.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (biometricEnabled && (mode is LockScreenMode.UnlockApp || mode is LockScreenMode.UnlockNote)) Icons.Default.Face else Icons.Default.Lock,
                        contentDescription = "Security Lock",
                        tint = colors.accent,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = titleText,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.text,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitleText,
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // PIN Dots Display with Shake
                Row(
                    modifier = Modifier
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        val dotColor = if (isError) Color(0xFFEF4444) else if (isFilled) colors.accent else colors.hairline
                        val dotScale by animateFloatAsState(
                            targetValue = if (isFilled) 1.18f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "dotScale_$i"
                        )

                        Box(
                            modifier = Modifier
                                .size(17.dp)
                                .scale(dotScale)
                                .clip(CircleShape)
                                .background(if (isFilled || isError) dotColor else colors.field)
                                .border(1.5.dp, dotColor, CircleShape)
                        )
                    }
                }

                // Error or Status text
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .padding(top = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }

            // Numeric Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Keypad Rows: [1,2,3], [4,5,6], [7,8,9]
                val digits = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in digits) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (digit in row) {
                            KeypadButton(
                                text = digit,
                                onClick = { onDigitPress(digit) }
                            )
                        }
                    }
                }

                // Bottom Row: [Face / Cancel, 0, Backspace]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left action: Face Unlock or Cancel
                    if (biometricEnabled && (mode is LockScreenMode.UnlockApp || mode is LockScreenMode.UnlockNote)) {
                        KeypadIconButton(
                            icon = Icons.Default.Face,
                            contentDescription = "Face Unlock",
                            badgeText = "Face",
                            onClick = { launchFaceUnlock() }
                        )
                    } else if (onDismiss != null) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(72.dp))
                    }

                    // Center: 0
                    KeypadButton(
                        text = "0",
                        onClick = { onDigitPress("0") }
                    )

                    // Right action: Backspace
                    KeypadIconButton(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete",
                        onClick = { onBackspacePress() },
                        onLongClick = { onClearPress() }
                    )
                }
            }
        }

        // Overlay: Real-Time Camera Face Scanner
        if (showInAppFaceScanner) {
            RealFaceCameraScanner(
                mode = RealFaceScannerMode.Unlock(
                    enrolledSignature = FaceSignature.fromJson(faceSignature),
                    onSuccess = {
                        showInAppFaceScanner = false
                        VibrationHelper.success(context)
                        onSuccess()
                    }
                ),
                onCancel = {
                    showInAppFaceScanner = false
                }
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, colors.hairline, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    badgeText: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, colors.hairline, CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.text,
                modifier = Modifier.size(24.dp)
            )
            if (badgeText != null) {
                Text(
                    text = badgeText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
