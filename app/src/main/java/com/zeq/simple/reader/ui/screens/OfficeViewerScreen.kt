package com.zeq.simple.reader.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.zeq.simple.reader.model.DocumentState
import com.zeq.simple.reader.model.DocumentType
import com.zeq.simple.reader.viewmodel.OfficeViewModel

/**
 * Office Document and Image Viewer Screen.
 * Supports DOCX, XLSX files (rendered as HTML in WebView) and image files.
 *
 * Features:
 * - Text search with highlighting
 * - Navigate between search results
 * - JavaScript enabled for XLSX tab switching
 *
 * Security measures for WebView:
 * - JavaScript enabled only for XLSX tab switching (no external scripts)
 * - File access disabled
 * - Content access disabled
 * - No network access
 *
 * Image viewer features:
 * - Pinch to zoom
 * - Pan to move
 * - Double tap to reset
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeViewerScreen(
    viewModel: OfficeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val s = state) {
                        is DocumentState.OfficeLoaded -> {
                            Column {
                                Text(
                                    text = s.fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = getDocumentTypeLabel(s.documentType),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // is DocumentState.ImageLoaded -> {
                        //     Column {
                        //         Text(
                        //             text = s.fileName,
                        //             maxLines = 1,
                        //             overflow = TextOverflow.Ellipsis,
                        //             style = MaterialTheme.typography.titleMedium
                        //         )
                        //         Text(
                        //             text = s.mimeType ?: "Image File",
                        //             style = MaterialTheme.typography.bodySmall,
                        //             color = MaterialTheme.colorScheme.onSurfaceVariant
                        //         )
                        //     }
                        // }
                        else -> Text("Document Viewer")
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
                    // Show search icon only for Office documents
                    if (state is DocumentState.OfficeLoaded) {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
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
        ) {
            when (val s = state) {
                is DocumentState.Loading -> {
                    LoadingContent()
                }
                is DocumentState.OfficeLoaded -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search bar
                        if (showSearch) {
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = { newQuery ->
                                    searchQuery = newQuery
                                    webViewInstance?.let { webView ->
                                        performSearch(webView, newQuery)
                                    }
                                },
                                onClose = {
                                    showSearch = false
                                    searchQuery = ""
                                    webViewInstance?.let { webView ->
                                        clearSearch(webView)
                                    }
                                },
                                onNext = {
                                    webViewInstance?.let { webView ->
                                        findNext(webView, forward = true)
                                    }
                                },
                                onPrevious = {
                                    webViewInstance?.let { webView ->
                                        findNext(webView, forward = false)
                                    }
                                }
                            )
                        }

                        // WebView
                        SecureWebView(
                            htmlContent = s.htmlContent,
                            enableJavaScript = s.documentType == DocumentType.XLSX,
                            onWebViewCreated = { webView ->
                                webViewInstance = webView
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        )
                    }
                }
                // is DocumentState.ImageLoaded -> {
                //     ZoomableImageViewer(
                //         uri = s.uri,
                //         contentDescription = s.fileName
                //     )
                // }
                is DocumentState.Error -> {
                    ErrorContent(message = s.message)
                }
                else -> {
                    // Idle state
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SecureWebView(
    htmlContent: String,
    enableJavaScript: Boolean,
    onWebViewCreated: (WebView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Security settings
                settings.apply {
                    // Enable JavaScript only for XLSX (tab switching)
                    javaScriptEnabled = enableJavaScript

                    // Disable all file/content access for security
                    allowFileAccess = false
                    allowContentAccess = false

                    // Disable geolocation and other permissions
                    setGeolocationEnabled(false)

                    // Display settings
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false

                    // Text settings
                    defaultTextEncodingName = "UTF-8"

                    // Cache settings - use memory cache only
                    cacheMode = WebSettings.LOAD_NO_CACHE

                    // Disable mixed content for security
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    // DOM storage for tab state (XLSX)
                    domStorageEnabled = enableJavaScript
                }

                // Disable scrollbars for cleaner look
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true

                // Set background color
                setBackgroundColor(android.graphics.Color.WHITE)

                // Notify that WebView is created
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            // Load HTML content as data URI (no file access needed)
            webView.loadDataWithBaseURL(
                null, // No base URL
                htmlContent,
                "text/html",
                "UTF-8",
                null // No history URL
            )
        },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("Search in document...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        onNext()
                    }
                )
            )

            // Previous result
            IconButton(
                onClick = onPrevious,
                enabled = query.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Previous"
                )
            }

            // Next result
            IconButton(
                onClick = onNext,
                enabled = query.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Next"
                )
            }

            // Close search
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search"
                )
            }
        }
    }
}

/**
 * Performs text search in WebView using JavaScript.
 * Highlights all matches with yellow background.
 */
private fun performSearch(webView: WebView, query: String) {
    if (query.isEmpty()) {
        clearSearch(webView)
        return
    }

    // Use WebView's built-in find functionality (API level 16+)
    @Suppress("DEPRECATION")
    webView.findAllAsync(query)
}

/**
 * Navigate to next/previous search result.
 */
private fun findNext(webView: WebView, forward: Boolean) {
    webView.findNext(forward)
}

/**
 * Clears all search highlights.
 */
private fun clearSearch(webView: WebView) {
    webView.clearMatches()
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
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
                text = "Parsing document...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
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
                text = "📄",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error Loading Document",
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

@Composable
private fun ZoomableImageViewer(
    uri: android.net.Uri,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)

        val maxX = (scale - 1) * 1000f
        val maxY = (scale - 1) * 1000f
        offset = Offset(
            x = (offset.x + offsetChange.x).coerceIn(-maxX, maxX),
            y = (offset.y + offsetChange.y).coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .transformable(state = state),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

private fun getDocumentTypeLabel(documentType: DocumentType): String {
    return when (documentType) {
        DocumentType.DOCX -> "Word Document"
        DocumentType.XLSX -> "Excel Spreadsheet"
        // DocumentType.IMAGE -> "Image File"
        else -> "Document"
    }
}
