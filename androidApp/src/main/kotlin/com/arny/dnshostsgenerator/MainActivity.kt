package com.arny.dnshostsgenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.tooling.preview.Preview
import com.arny.dnshostsgenerator.logging.AppLogger
import com.arny.dnshostsgenerator.platform.AndroidArchivePicker
import com.arny.dnshostsgenerator.platform.setAndroidAppContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setAndroidAppContext(this)
        AndroidArchivePicker.contentResolver = contentResolver
        AppLogger.d("MainActivity created, Android context initialized")

        setContent {
            AndroidArchivePickerSetup()
            App()
        }
    }
}

/**
 * Регистрирует системный выбор файла и отдаёт его shared-коду через [AndroidArchivePicker].
 * Нужен для импорта zip-паков со списками доменов.
 */
@Composable
private fun AndroidArchivePickerSetup() {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        AndroidArchivePicker.deliverPickedUri?.invoke(uri)
    }
    DisposableEffect(launcher) {
        AndroidArchivePicker.openFileDialog = {
            launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
        onDispose { AndroidArchivePicker.openFileDialog = null }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}