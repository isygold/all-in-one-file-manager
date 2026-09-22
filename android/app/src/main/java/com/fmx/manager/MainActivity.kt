package com.fmx.manager

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fmx.manager.ui.FmxApp
import com.fmx.manager.ui.FmxTheme
import com.fmx.manager.ui.ThemeMode

class VmFactory(private val ctx: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BrowserViewModel(ctx.applicationContext) as T
}

class MainActivity : ComponentActivity() {
    private val vm: BrowserViewModel by viewModels { VmFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askStoragePermission()
        setContent {
            val prefs = remember { Prefs(applicationContext) }
            var themeName by remember { mutableStateOf(prefs.theme) }
            var dynamic by remember { mutableStateOf(prefs.dynamicColor) }
            val mode = remember(themeName) {
                runCatching { ThemeMode.valueOf(themeName.uppercase()) }
                    .getOrDefault(ThemeMode.SYSTEM)
            }
            FmxTheme(mode = mode, dynamic = dynamic) {
                FmxApp(
                    vm = vm,
                    prefs = prefs,
                    onThemeChange = { m, d ->
                        themeName = m.name.lowercase()
                        prefs.theme = themeName
                        dynamic = d
                        prefs.dynamicColor = d
                    },
                )
            }
        }
    }

    private fun askStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(
                        android.content.Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                } catch (_: Exception) {
                }
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 23 &&
            (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
                41,
            )
        }
    }
}
