package com.example.authenticator.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScannerScreen(onDetected: (String) -> Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var permitted by remember { mutableStateOf(hasPermission()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permitted = hasPermission() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!permitted) {
        CenterMessage("Доступ к камере", "Камера нужна для чтения QR-кода 2FA. Изображения обрабатываются на устройстве.") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text("Разрешить камеру") }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                }) { Text("Настройки разрешений") }
            }
        }
    } else {
        CameraPreview(onDetected)
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onDetected: (String) -> Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val view = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val currentCallback by rememberUpdatedState(onDetected)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, view, generation) {
        problem = null
        val closed = AtomicBoolean(false)
        val processing = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var provider: ProcessCameraProvider? = null
        var delivered = false
        var rejected: String? = null
        analysis.setAnalyzer(executor) { proxy ->
            val media = proxy.image
            if (closed.get() || media == null || !processing.compareAndSet(false, true)) {
                proxy.close()
            } else {
                try {
                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(image).addOnSuccessListener(mainExecutor) { barcodes ->
                        if (!closed.get() && !delivered) {
                            val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
                            if (raw != null && raw != rejected) {
                                delivered = true
                                if (!currentCallback(raw)) {
                                    delivered = false
                                    rejected = raw
                                    problem = "Этот QR-код не удалось добавить. Проверьте, что это QR-код настройки TOTP."
                                }
                            }
                        }
                    }.addOnFailureListener(mainExecutor) {
                        if (!closed.get()) problem = "Не удалось распознать изображение. Попробуйте ещё раз."
                    }.addOnCompleteListener(mainExecutor) {
                        proxy.close()
                        processing.set(false)
                        if (closed.get()) scanner.close()
                    }
                } catch (_: Exception) {
                    proxy.close()
                    processing.set(false)
                    if (closed.get()) scanner.close()
                    mainExecutor.execute { if (!closed.get()) problem = "Ошибка обработки кадра. Повторите сканирование." }
                }
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!closed.get()) {
                try {
                    provider = future.get()
                    camera = provider!!.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) { problem = "Камера недоступна. Закройте другие приложения с камерой или введите ключ вручную." }
            }
        }, mainExecutor)
        onDispose {
            closed.set(true)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            camera = null
            if (!processing.get()) scanner.close()
            executor.shutdown()
        }
    }
    LaunchedEffect(camera, torch) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Text("Наведите камеру на QR-код сервиса", color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(24.dp),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.align(Alignment.Center).size(244.dp).border(2.dp, Color.White, RoundedCornerShape(28.dp)))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .7f)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            problem?.let {
                Text(it, color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = { generation++; torch = false }) { Text("Повторить") }
            }
            Text("После сканирования проверьте данные и нажмите «Сохранить».", color = Color.White, textAlign = TextAlign.Center)
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(onClick = { torch = !torch }) {
                    Icon(if (torch) Icons.Outlined.FlashOff else Icons.Outlined.FlashOn,
                        if (torch) "Выключить фонарик" else "Включить фонарик", tint = Color.White)
                }
            }
        }
    }
}
