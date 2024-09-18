package com.example.camerax

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.face.Face

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
fun DetectedFaces(
    faces: List<Face>,
    sourceInfo: SourceInfo,
) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val needToMirror = sourceInfo.isImageFlipped

        faces.forEach { face ->
            val left = if (needToMirror) size.width - face.boundingBox.right.toFloat() else face.boundingBox.left.toFloat()
            drawRect(
                Color.Cyan, style = Stroke(1.dp.toPx()),
                topLeft = Offset(left, face.boundingBox.top.toFloat()),
                size = Size(face.boundingBox.width().toFloat(), face.boundingBox.height().toFloat())
            )
        }
    }
}