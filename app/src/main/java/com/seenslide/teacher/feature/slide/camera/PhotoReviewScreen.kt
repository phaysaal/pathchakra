package com.seenslide.teacher.feature.slide.camera

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seenslide.teacher.R
import androidx.compose.material.icons.filled.Draw
import com.seenslide.teacher.core.media.ImageEnhancer

@Composable
fun PhotoReviewScreen(
    photoPath: String,
    isUploading: Boolean,
    onAccept: (enhanced: Boolean) -> Unit,
    onRetake: () -> Unit,
    onDrawOnPhoto: ((String) -> Unit)? = null,
) {
    val originalBitmap = remember(photoPath) { BitmapFactory.decodeFile(photoPath) }
    val enhancer = remember { ImageEnhancer() }
    var enhanceEnabled by remember { mutableStateOf(true) }

    val displayBitmap = remember(photoPath, enhanceEnabled) {
        if (enhanceEnabled && originalBitmap != null) {
            enhancer.enhance(originalBitmap)
        } else {
            originalBitmap
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Photo preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Captured photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
        }

        // Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Enhance toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto-enhance",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enhanceEnabled,
                    onCheckedChange = { enhanceEnabled = it },
                    enabled = !isUploading,
                )
            }

            // Draw on photo button
            if (onDrawOnPhoto != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDrawOnPhoto(photoPath) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isUploading,
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Draw on Photo")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = !isUploading,
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.retake))
                }

                Button(
                    onClick = { onAccept(enhanceEnabled) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = !isUploading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save_slide))
                }
            }
        }
    }
}
