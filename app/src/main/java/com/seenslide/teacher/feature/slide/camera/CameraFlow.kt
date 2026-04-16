package com.seenslide.teacher.feature.slide.camera

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext

/**
 * Orchestrates camera capture -> review -> upload flow.
 * Also supports "Draw on Photo" to open the slide editor with the photo as background.
 */
@Composable
fun CameraFlow(
    viewModel: CameraViewModel,
    onSlideUploaded: () -> Unit,
    onClose: () -> Unit,
    onDrawOnPhoto: ((String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.uploadSuccess) {
        if (uiState.uploadSuccess) {
            Toast.makeText(context, "Slide saved!", Toast.LENGTH_SHORT).show()
            viewModel.onUploadSuccessHandled()
            onSlideUploaded()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    if (uiState.capturedPhotoPath != null) {
        PhotoReviewScreen(
            photoPath = uiState.capturedPhotoPath!!,
            isUploading = uiState.isUploading,
            onAccept = viewModel::onAccept,
            onRetake = viewModel::onRetake,
            onDrawOnPhoto = onDrawOnPhoto,
        )
    } else {
        CameraScreen(
            onPhotoCaptured = { file -> viewModel.onPhotoCaptured(file.absolutePath) },
            onClose = onClose,
        )
    }
}
