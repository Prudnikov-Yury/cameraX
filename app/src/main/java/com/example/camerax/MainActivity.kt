package com.example.camerax

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview.Builder
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.camerax.ui.theme.CameraXTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
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
                val detectedBarcode = remember { mutableStateOf<Barcode?>(null) }


                val cameraController = remember {
                    LifecycleCameraController(applicationContext)
                        .configure(
                            applicationContext,
                            onBarcodeDetected = {
                                detectedBarcode.value = it
                            }
                        )
                }

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
                            cameraController = cameraController,
                            paddingValues = innerPadding,
                            onChangeCamera = {
                                cameraController.cameraSelector =
                                    switchLens(cameraController.cameraSelector)
                            },
                            onGalleryClick = {
                                scope.launch {
                                    scaffoldState.bottomSheetState.expand()
                                }
                            },
                            onTakePhoto = {
                                takePhoto(
                                    cameraController,
                                    onPhotoTaken = viewModel::onTakePhoto
                                )
                            }
                        )

                        DetectedBarcodes(barcode = detectedBarcode)
                    }
                }

                BackHandler { isCameraStarted = false }
            }
        }
    }

    private fun LifecycleCameraController.configure(
        context: Context,
        onBarcodeDetected: (Barcode?) -> Unit
    ): LifecycleCameraController {
        return apply {
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()

            val barcodeScanner = BarcodeScanning.getClient(options)

            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                MlKitAnalyzer(
                    listOf(barcodeScanner),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    ContextCompat.getMainExecutor(context)
                ) { result: MlKitAnalyzer.Result? ->


                    val barcodeResults = result?.getValue(barcodeScanner)
                    if ((barcodeResults == null) ||
                        (barcodeResults.size == 0) ||
                        (barcodeResults.first() == null)
                    ) {
                        onBarcodeDetected(null)
                        return@MlKitAnalyzer
                    }
                    onBarcodeDetected(barcodeResults[0])
                }
            )
        }
    }

    @Composable
    private fun Camera(
        cameraController: LifecycleCameraController,
        paddingValues: PaddingValues,
        onChangeCamera: () -> Unit,
        onGalleryClick: () -> Unit,
        onTakePhoto: () -> Unit
    ) {

        val previewView: PreviewView = remember {
            PreviewView(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_START
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = cameraController
                cameraController.bindToLifecycle(this@MainActivity)
            }
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
                    contentDescription = null
                )
            }
            IconButton(onClick = onTakePhoto) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null
                )
            }
        }

    }

    private fun takePhoto(
        cameraController: LifecycleCameraController,
        onPhotoTaken: (Bitmap) -> Unit
    ) {
        cameraController.takePicture(
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

    private fun switchLens(lens: CameraSelector) = if (lens == CameraSelector.DEFAULT_BACK_CAMERA) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else CameraSelector.DEFAULT_BACK_CAMERA

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
    vararg useCases: UseCase?,
    onBarcodeDetected: (String) -> Unit
): ListenableFuture<ProcessCameraProvider> {
    addListener(
        {
            val preview = Builder()
                .build()
                .apply {
                    surfaceProvider = previewView.surfaceProvider
                }

            val analysis = bindAnalysisUseCase(context, onBarcodeDetected)

            get().apply {
                unbindAll()
                bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(cameraSelector).build(),
                    preview,
                    *useCases,
                    analysis
                )
            }

        }, ContextCompat.getMainExecutor(context)
    )
    return this
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
fun bindAnalysisUseCase(
    context: Context,
    onBarcodeDetected: (String) -> Unit
): ImageAnalysis {
    val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    imageAnalysis.setAnalyzer(
        ContextCompat.getMainExecutor(context)
    ) { imageProxy ->

        val image = imageProxy.image
        if (image != null) {

            val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()

            val scanner = BarcodeScanning.getClient(options)

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    for (barcode: Barcode in barcodes) {
                        onBarcodeDetected(barcode.rawValue ?: "No barcode")
                        Log.d("onBarcodeDetected", "bindAnalysisUseCase: ${barcode.rawValue}")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Barcode scanning failed: ${e.message}", e)
                }
        }
        imageProxy.close()
    }

    return imageAnalysis
}

@Composable
fun DetectedBarcodes(
    barcode: State<Barcode?>
) {
    if (barcode.value == null) return

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        drawRect(
            color = Color.Cyan,
            style = Stroke(2.dp.toPx()),
            topLeft = Offset(
                barcode.value?.boundingBox?.left?.toFloat() ?: 0f,
                barcode.value?.boundingBox?.top?.toFloat() ?: 0f,
            ),
            size = Size(
                barcode.value?.boundingBox?.width()?.toFloat() ?: 0f,
                barcode.value?.boundingBox?.height()?.toFloat() ?: 0f
            )
        )
    }

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