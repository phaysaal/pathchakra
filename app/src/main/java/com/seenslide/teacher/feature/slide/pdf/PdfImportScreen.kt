package com.seenslide.teacher.feature.slide.pdf

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seenslide.teacher.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(
    viewModel: PdfViewModel,
    onDrawOnPage: (photoPath: String) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadPdf(it) }
    }

    // Auto-open picker if no PDF loaded
    LaunchedEffect(Unit) {
        if (!uiState.pdfLoaded) {
            pdfPicker.launch(arrayOf("application/pdf"))
        }
    }

    LaunchedEffect(uiState.uploadSuccess) {
        if (uiState.uploadSuccess) {
            Toast.makeText(context, context.getString(R.string.slide_saved), Toast.LENGTH_SHORT).show()
            viewModel.onUploadSuccessHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.pdfLoaded) {
                        Text(stringResource(R.string.page_x_of_y, uiState.currentPage + 1, uiState.pageCount))
                    } else {
                        Text(stringResource(R.string.from_pdf))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (!uiState.pdfLoaded) {
            // No PDF loaded — show open button
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                    Text(stringResource(R.string.open_pdf_file))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Page display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    uiState.pageBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF page ${uiState.currentPage + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    if (uiState.isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }

                // Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .navigationBarsPadding()
                        .padding(12.dp),
                ) {
                    // Page navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = viewModel::previousPage,
                            enabled = uiState.currentPage > 0,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous page")
                        }

                        Text(
                            text = "${uiState.currentPage + 1} / ${uiState.pageCount}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )

                        IconButton(
                            onClick = viewModel::nextPage,
                            enabled = uiState.currentPage < uiState.pageCount - 1,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next page")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Save page as slide directly
                        Button(
                            onClick = viewModel::savePageAsSlide,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            enabled = !uiState.isUploading,
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.save_slide))
                        }

                        // Draw on page
                        OutlinedButton(
                            onClick = {
                                val path = viewModel.savePageToTempFile()
                                if (path != null) onDrawOnPage(path)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            enabled = !uiState.isUploading,
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.draw_on_page))
                        }
                    }
                }
            }
        }
    }
}
