package com.example.camerax

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.camerax.ui.theme.CameraXTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraXTheme {
                var isCameraStarted by remember { mutableStateOf(false) }

                val launcher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                        if (isGranted) {
                            isCameraStarted = true
                        } else {
                            Log.d("MainActivity", "camera permission denied")
                        }
                    }


                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    if (!isCameraStarted) {
                        StartCameraButton(
                            modifier = Modifier.padding(innerPadding),
                            onStartCameraClick = {
                                if (!permissionGranted()) {
                                    launcher.launch(Manifest.permission.CAMERA)
                                } else {
                                    isCameraStarted = true
                                }
                            }
                        )
                    } else {
                        Camera()
                    }
                }

                BackHandler {
                    isCameraStarted = false
                }
            }
        }
    }

    @Composable
    private fun Camera() {
        val previewView = remember { PreviewView(this) }
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(this) }

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e("ObjectDetection", "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))

        AndroidView(
            factory = {
                previewView
            },
            update = {

            }
        )
    }


    private fun permissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}


@Composable
fun StartCameraButton(
    modifier: Modifier = Modifier,
    onStartCameraClick: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Button(
            modifier = Modifier.align(Alignment.Center),
            onClick = onStartCameraClick,
            content = {
                Text("Start Camera")
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CameraXTheme {
        StartCameraButton(onStartCameraClick = {})
    }
}