package com.zeq.simple.reader.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zeq.simple.reader.model.DocumentState
import com.zeq.simple.reader.model.DocumentType
import com.zeq.simple.reader.viewmodel.OfficeViewModel

/**
 * Office Document Viewer Screen for DOCX and XLSX files.
 * Renders parsed HTML content in a secure WebView.
 *
 * Security measures:
 * - JavaScript enabled only for XLSX tab switching (no external scripts)
 * - File access disabled
 * - Content access disabled
 * - No network access
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeViewerScreen(
    viewModel: OfficeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

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
                    SecureWebView(
                        htmlContent = s.htmlContent,
                        enableJavaScript = s.documentType == DocumentType.XLSX
                    )
                }
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

private fun getDocumentTypeLabel(documentType: DocumentType): String {
    return when (documentType) {
        DocumentType.DOCX -> "Word Document"
        DocumentType.XLSX -> "Excel Spreadsheet"
        else -> "Document"
    }
}
