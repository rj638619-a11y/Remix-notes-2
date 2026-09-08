package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.home.HomeScreen
import com.example.ui.security.AppLockScreen
import com.example.ui.security.LockScreenMode
import com.example.ui.theme.GlassNotesTheme
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.LocalThemeTransition
import com.example.ui.theme.ThemeLightWaveOverlay
import com.example.ui.theme.ThemeTransitionState
import com.example.ui.viewmodel.NotesViewModel

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "EXTRA_OPEN_NOTE_ID"
        const val EXTRA_ACTION = "EXTRA_ACTION"
        const val ACTION_CREATE = "create"
    }

    private val viewModel: NotesViewModel by viewModels {
        NotesViewModel.provideFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
            val themeTransitionState = remember { ThemeTransitionState() }
            val coroutineScope = rememberCoroutineScope()
            val isSystemDark = isSystemInDarkTheme()

            val effectiveDark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }

            var lastKnownDark by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(effectiveDark) {
                if (lastKnownDark != null && lastKnownDark != effectiveDark) {
                    themeTransitionState.startTransition(
                        toDark = effectiveDark,
                        scope = coroutineScope
                    )
                }
                lastKnownDark = effectiveDark
            }

            CompositionLocalProvider(LocalThemeTransition provides themeTransitionState) {
                GlassNotesTheme(
                    themeSetting = settings.theme,
                    reduceTransparency = settings.reduceTransparency
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = GlassTheme.colors.bg
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            HomeScreen(viewModel = viewModel)
                            ThemeLightWaveOverlay(state = themeTransitionState)

                            if (isAppLocked && (settings.appLockEnabled || settings.pinCode.isNotBlank())) {
                                AppLockScreen(
                                    mode = LockScreenMode.UnlockApp,
                                    correctPin = settings.pinCode,
                                    biometricEnabled = settings.biometricLockEnabled,
                                    faceSignature = settings.faceSignature,
                                    faceEnrolled = settings.faceEnrolled,
                                    onSuccess = { viewModel.unlockApp() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (viewModel.settings.value.appLockEnabled && viewModel.settings.value.pinCode.isNotBlank()) {
            viewModel.lockApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val openNoteId = intent.getStringExtra(EXTRA_OPEN_NOTE_ID)
        val action = intent.getStringExtra(EXTRA_ACTION)

        if (!openNoteId.isNullOrBlank()) {
            viewModel.handleWidgetOpenNote(openNoteId)
        } else if (action == ACTION_CREATE) {
            viewModel.handleWidgetCreateNote()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            System.gc()
        }
    }

    private val requestCodeMap = mutableMapOf<Int, Int>()

    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        startActivityForResult(intent, requestCode, null)
    }

    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        val safeCode = if ((requestCode and 0xffff0000.toInt()) != 0) {
            var candidate = requestCode and 0x0000ffff
            if (candidate == 0) candidate = 1000
            while (requestCodeMap.containsKey(candidate)) {
                candidate = (candidate + 1) and 0x0000ffff
                if (candidate == 0) candidate = 1000
            }
            candidate
        } else {
            requestCode
        }
        requestCodeMap[safeCode] = requestCode
        super.startActivityForResult(intent, safeCode, options)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val originalCode = requestCodeMap.remove(requestCode)
        if (originalCode != null) {
            if (!activityResultRegistry.dispatchResult(originalCode, resultCode, data)) {
                super.onActivityResult(originalCode, resultCode, data)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
