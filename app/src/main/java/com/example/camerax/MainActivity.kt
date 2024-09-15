package com.example.camerax

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview.Builder
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.camerax.ui.theme.CameraXTheme
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraXTheme {
                val viewModel = viewModel<MainViewModel>()
                val bitmaps by viewModel.bitmaps.collectAsState()
                val scope = rememberCoroutineScope()
                val scaffoldState = rememberBottomSheetScaffoldState()
                var isCameraStarted by remember { mutableStateOf(false) }
                var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
                val imageCapture = remember { ImageCapture.Builder().build() }
                val useCases = remember { arrayOf(imageCapture) }


                val launcher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                        if (isGranted) {
                            isCameraStarted = true
                        } else {
                            Log.d("MainActivity", "camera permission denied")
                        }
                    }

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 0.dp,
                    sheetContent = {
                        BottomSheetContent(
                            bitmaps = bitmaps,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                        Camera(
                            paddingValues = innerPadding,
                            cameraSelector = lens,
                            useCases = useCases,
                            onChangeCamera = {
                                lens = switchLens(lens)
                            },
                            onGalleryClick = {
                                scope.launch {
                                    scaffoldState.bottomSheetState.expand()
                                }
                            },
                            onTakePhoto = {
                                takePhoto(
                                    imageCapture,
                                    onPhotoTaken = viewModel::onTakePhoto
                                )
                            }
                        )
                    }

                }

                BackHandler { isCameraStarted = false }
            }
        }
    }

    @Composable
    private fun Camera(
        paddingValues: PaddingValues,
        cameraSelector: Int,
        vararg useCases: UseCase?,
        onChangeCamera: () -> Unit,
        onGalleryClick: () -> Unit,
        onTakePhoto: () -> Unit
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val previewView: PreviewView = remember { PreviewView(this) }

        remember(key1 = cameraSelector) {
            ProcessCameraProvider.getInstance(this)
                .configureCamera(
                    previewView = previewView,
                    lifecycleOwner = lifecycleOwner,
                    cameraSelector = cameraSelector,
                    context = context,
                    useCases = useCases
                )
        }

        CameraContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            previewView = previewView,
            onChangeCamera = onChangeCamera,
            onGalleryClick = onGalleryClick,
            onTakePhoto = onTakePhoto
        )
    }

    @Composable
    fun CameraContent(
        modifier: Modifier = Modifier,
        previewView: PreviewView,
        onChangeCamera: () -> Unit,
        onGalleryClick: () -> Unit,
        onTakePhoto: () -> Unit
    ) {

        Box(modifier = modifier) {
            AndroidView(
                factory = {
                    previewView
                }
            )

            Controls(
                onChangeCamera = onChangeCamera,
                onGalleryClick = onGalleryClick,
                onTakePhoto = onTakePhoto
            )
        }

    }

    @Composable
    fun BoxScope.Controls(
        onChangeCamera: () -> Unit,
        onGalleryClick: () -> Unit,
        onTakePhoto: () -> Unit
    ) {

        IconButton(
            onClick = onChangeCamera,
            modifier = Modifier.offset(16.dp, 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch camera"
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            IconButton(onClick = onGalleryClick) {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = "Open gallery"
                )
            }
            IconButton(onClick = onTakePhoto) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Take photo"
                )
            }
        }

    }

    private fun takePhoto(
        imageCapture: ImageCapture,
        onPhotoTaken: (Bitmap) -> Unit
    ) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                    }

                    val rotatedBitmap = Bitmap.createBitmap(
                        image.toBitmap(),
                        0,
                        0,
                        image.width,
                        image.height,
                        matrix,
                        true
                    )

                    onPhotoTaken(rotatedBitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.e("MainActivity", "Невозможно сфотографировать: ", exception)
                }
            }
        )
    }

    private fun switchLens(lens: Int) = if (CameraSelector.LENS_FACING_FRONT == lens) {
        CameraSelector.LENS_FACING_BACK
    } else {
        CameraSelector.LENS_FACING_FRONT
    }


    private fun permissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}


private fun ListenableFuture<ProcessCameraProvider>.configureCamera(
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    cameraSelector: Int,
    context: Context,
    vararg useCases: UseCase?
): ListenableFuture<ProcessCameraProvider> {
    addListener(
        {
            val preview = Builder()
                .build()
                .apply {
                    surfaceProvider = previewView.surfaceProvider
                }

            get().apply {
                unbindAll()
                bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(cameraSelector).build(),
                    preview,
                    *useCases
                )
            }

        }, ContextCompat.getMainExecutor(context)
    )
    return this
}

@Composable
fun BottomSheetContent(
    bitmaps: List<Bitmap>,
    modifier: Modifier = Modifier
) {
    if (bitmaps.isEmpty()) {
        Box(
            modifier = modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Пусто и точка")
        }
    } else {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 16.dp,
            contentPadding = PaddingValues(16.dp),
            modifier = modifier
        ) {
            items(bitmaps) { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                )
            }
        }
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