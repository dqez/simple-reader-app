package com.zeq.simple.reader.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeq.simple.reader.model.DocumentState
import com.zeq.simple.reader.model.PdfPage
import com.zeq.simple.reader.viewmodel.PdfViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * PDF Viewer Screen using native PdfRenderer.
 * Displays PDF pages in a scrollable LazyColumn with bitmap caching.
 *
 * Features:
 * - Virtual scrolling with on-demand page rendering
 * - Page indicator showing current page / total pages
 * - Pinch-to-zoom support
 * - Memory-efficient bitmap recycling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()

    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val screenWidth = with(LocalDensity.current) {
        configuration.screenWidthDp.dp.toPx().toInt()
    }

    // GLOBAL zoom state - shared across all pages
    var globalScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Track visible pages and trigger rendering
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex
        }.distinctUntilChanged().collect { visibleIndex ->
            viewModel.onPageVisible(visibleIndex, screenWidth)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val s = state) {
                        is DocumentState.PdfLoaded -> {
                            Column {
                                Text(
                                    text = s.fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Page ${currentPage + 1} of ${s.pageCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> Text("PDF Viewer")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Zoom Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                globalScale = (globalScale - 0.25f).coerceIn(0.5f, 3f)
                                if (globalScale <= 1f) { offsetX = 0f; offsetY = 0f }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("−", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "${(globalScale * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        FilledTonalIconButton(
                            onClick = {
                                globalScale = (globalScale + 0.25f).coerceIn(0.5f, 3f)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            when (val s = state) {
                is DocumentState.Loading -> {
                    LoadingIndicator()
                }
                is DocumentState.PdfLoaded -> {
                    PdfContentWithGlobalZoom(
                        pages = pages,
                        listState = listState,
                        screenWidth = screenWidth,
                        globalScale = globalScale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        onScaleChange = { newScale ->
                            globalScale = newScale.coerceIn(0.5f, 3f)
                            if (globalScale <= 1f) { offsetX = 0f; offsetY = 0f }
                        },
                        onOffsetChange = { dx, dy ->
                            if (globalScale > 1f) {
                                offsetX += dx
                                offsetY += dy
                            }
                        },
                        onPageVisible = { index ->
                            viewModel.renderPage(index, screenWidth)
                        }
                    )
                }
                is DocumentState.Error -> {
                    ErrorMessage(message = s.message)
                }
                else -> {
                    // Idle state
                }
            }
        }
    }
}

@Composable
private fun PdfContentWithGlobalZoom(
    pages: List<PdfPage>,
    listState: LazyListState,
    screenWidth: Int,
    globalScale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onPageVisible: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onScaleChange(globalScale * zoom)
                    onOffsetChange(pan.x, pan.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // Double-tap to toggle zoom
                        if (globalScale > 1f) {
                            onScaleChange(1f)
                        } else {
                            onScaleChange(2f)
                        }
                    }
                )
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = globalScale,
                    scaleY = globalScale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            itemsIndexed(
                items = pages,
                key = { index, _ -> index }
            ) { index, page ->
                // Trigger rendering when page becomes visible
                LaunchedEffect(index) {
                    onPageVisible(index)
                }

                PdfPageItemSimple(
                    page = page,
                    pageNumber = index + 1
                )
            }
        }
    }
}

@Composable
private fun PdfPageItemSimple(
    page: PdfPage,
    pageNumber: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                    if (page.width > 0 && page.height > 0) {
                        page.width.toFloat() / page.height.toFloat()
                    } else {
                        0.707f // A4 aspect ratio fallback
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                page.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                page.bitmap != null && !page.bitmap.isRecycled -> {
                    Image(
                        bitmap = page.bitmap.asImageBitmap(),
                        contentDescription = "PDF Page $pageNumber",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFAFAFA)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Page $pageNumber",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading PDF...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error Loading PDF",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
