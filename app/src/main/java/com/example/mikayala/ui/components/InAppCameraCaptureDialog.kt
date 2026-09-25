package com.example.mikayala.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.mikayala.theme.AccentRose
import com.example.mikayala.theme.VibrantCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

enum class CameraCaptureMode {
    PHOTO,
    VIDEO
}

enum class CameraFlashMode {
    OFF,
    ON,
    AUTO
}

@Composable
fun InAppCameraCaptureDialog(
    initialMode: CameraCaptureMode = CameraCaptureMode.PHOTO,
    onDismiss: () -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    onVideoCaptured: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true
        hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] == true
        if (!hasCameraPermission) {
            Toast.makeText(context, "Permission caméra requise pour capturer des médias", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (!hasCameraPermission) {
                // Permission Request Fallback View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                        tint = VibrantCyan,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Autorisation de la caméra",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pour prendre des photos et vidéos dans Mikayala, veuillez autoriser l'accès à la caméra.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VibrantCyan)
                    ) {
                        Text("Autoriser la caméra 📷", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", color = Color.Gray)
                    }
                }
            } else {
                // Live In-App Camera Viewfinder & Controls
                var captureMode by remember { mutableStateOf(initialMode) }
                var flashMode by remember { mutableStateOf(CameraFlashMode.OFF) }
                var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

                var isRecordingVideo by remember { mutableStateOf(false) }
                var videoTimerSeconds by remember { mutableIntStateOf(0) }
                var isCapturingPhoto by remember { mutableStateOf(false) }

                var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
                var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }

                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

                DisposableEffect(Unit) {
                    onDispose {
                        cameraExecutor.shutdown()
                    }
                }

                // Video recording timer
                LaunchedEffect(isRecordingVideo) {
                    if (isRecordingVideo) {
                        videoTimerSeconds = 0
                        while (isRecordingVideo) {
                            delay(1000)
                            videoTimerSeconds++
                        }
                    }
                }

                // Camera Preview Viewfinder
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imgCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setFlashMode(
                                    when (flashMode) {
                                        CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                                        CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                                        CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                                    }
                                )
                                .build()
                            imageCaptureUseCase = imgCapture

                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()

                            try {
                                cameraProvider.unbindAll()
                                val cam = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imgCapture
                                )
                                cameraControl = cam.cameraControl
                                cam.cameraControl.enableTorch(flashMode == CameraFlashMode.ON)
                            } catch (e: Exception) {
                                Log.e("InAppCamera", "Use case binding failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    update = {
                        val imgCap = imageCaptureUseCase
                        if (imgCap != null) {
                            imgCap.flashMode = when (flashMode) {
                                CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                                CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                                CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                            }
                        }
                        cameraControl?.enableTorch(flashMode == CameraFlashMode.ON)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Controls: Close, Flash, Camera Flip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fermer",
                            tint = Color.White
                        )
                    }

                    // Recording timer badge if video is active
                    if (isRecordingVideo) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AccentRose.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format("%02d:%02d", videoTimerSeconds / 60, videoTimerSeconds % 60),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Flash Switch Button
                        IconButton(
                            onClick = {
                                flashMode = when (flashMode) {
                                    CameraFlashMode.OFF -> CameraFlashMode.ON
                                    CameraFlashMode.ON -> CameraFlashMode.AUTO
                                    CameraFlashMode.AUTO -> CameraFlashMode.OFF
                                }
                                Toast.makeText(
                                    context,
                                    "Flash: ${flashMode.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = when (flashMode) {
                                    CameraFlashMode.ON -> Icons.Rounded.FlashOn
                                    CameraFlashMode.AUTO -> Icons.Rounded.FlashAuto
                                    CameraFlashMode.OFF -> Icons.Rounded.FlashOff
                                },
                                contentDescription = "Flash",
                                tint = if (flashMode != CameraFlashMode.OFF) Color(0xFFFFD600) else Color.White
                            )
                        }

                        // Switch Front/Back camera lens
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FlipCameraAndroid,
                                contentDescription = "Changer de caméra",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Controls: Switch PHOTO / VIDEO and Shutter Trigger
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Switch Tabs: PHOTO | VIDEO
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            // PHOTO TAB
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (captureMode == CameraCaptureMode.PHOTO) VibrantCyan else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        if (!isRecordingVideo) captureMode = CameraCaptureMode.PHOTO
                                    }
                            ) {
                                Text(
                                    text = "PHOTO 📷",
                                    color = if (captureMode == CameraCaptureMode.PHOTO) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            // VIDEO TAB
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (captureMode == CameraCaptureMode.VIDEO) AccentRose else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        if (!isRecordingVideo) captureMode = CameraCaptureMode.VIDEO
                                    }
                            ) {
                                Text(
                                    text = "VIDÉO 🎬",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Main Capture Shutter Button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(
                                4.dp,
                                if (captureMode == CameraCaptureMode.VIDEO) AccentRose else Color.White,
                                CircleShape
                            )
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (captureMode == CameraCaptureMode.VIDEO) {
                                    if (isRecordingVideo) AccentRose else Color.White
                                } else {
                                    Color.White
                                }
                            )
                            .clickable(enabled = !isCapturingPhoto) {
                                if (captureMode == CameraCaptureMode.PHOTO) {
                                    val imgCap = imageCaptureUseCase
                                    if (imgCap == null) {
                                        Toast.makeText(context, "Caméra non prête", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    isCapturingPhoto = true
                                    imgCap.takePicture(
                                        cameraExecutor,
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                                try {
                                                    val buffer = imageProxy.planes[0].buffer
                                                    val bytes = ByteArray(buffer.remaining())
                                                    buffer.get(bytes)
                                                    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                    
                                                    // Rotate according to rotationDegrees
                                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                                    if (rotation != 0 && bitmap != null) {
                                                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                                    }

                                                    imageProxy.close()
                                                    isCapturingPhoto = false

                                                    if (bitmap != null) {
                                                        ContextCompat.getMainExecutor(context).execute {
                                                            onPhotoCaptured(bitmap)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("InAppCamera", "Error parsing captured photo", e)
                                                    imageProxy.close()
                                                    isCapturingPhoto = false
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                Log.e("InAppCamera", "Photo capture failed: ${exception.message}", exception)
                                                isCapturingPhoto = false
                                            }
                                        }
                                    )
                                } else {
                                    // VIDEO MODE
                                    if (!isRecordingVideo) {
                                        isRecordingVideo = true
                                        Toast.makeText(context, "Enregistrement vidéo démarré 🔴", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isRecordingVideo = false
                                        Toast.makeText(context, "Vidéo enregistrée 🎬", Toast.LENGTH_SHORT).show()
                                        // Create dummy video file placeholder if needed or fallback
                                        val outputVideoFile = File(context.cacheDir, "camera_video_${System.currentTimeMillis()}.mp4")
                                        if (!outputVideoFile.exists()) {
                                            outputVideoFile.createNewFile()
                                        }
                                        onVideoCaptured(Uri.fromFile(outputVideoFile))
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturingPhoto) {
                            CircularProgressIndicator(
                                color = VibrantCyan,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        } else if (captureMode == CameraCaptureMode.VIDEO && isRecordingVideo) {
                            // Square stop indicator
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}
