package com.zeq.simple.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeq.simple.reader.model.DocumentType
import com.zeq.simple.reader.ui.screens.HomeScreen
import com.zeq.simple.reader.ui.screens.OfficeViewerScreen
import com.zeq.simple.reader.ui.screens.PdfViewerScreen
import com.zeq.simple.reader.ui.theme.ReaderTheme
import com.zeq.simple.reader.viewmodel.OfficeViewModel
import com.zeq.simple.reader.viewmodel.PdfViewModel

/**
 * Main entry point for the Simple Reader app.
 *
 * Handles:
 * - Normal app launch (shows home screen with file picker)
 * - Intent-based launch (opens file directly when used with "Open With")
 * - Content URI resolution from ContentResolver
 *
 * Architecture:
 * - Uses Compose for UI
 * - MVVM pattern with ViewModels for PDF and Office documents
 * - Scoped storage compliant (content:// URIs only)
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReaderApp(
                        initialIntent = intent,
                        onResolveUri = { uri -> resolveDocumentInfo(uri) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle new intent when activity already exists (singleTop launch mode)
        Log.d(TAG, "onNewIntent: ${intent.action}, data: ${intent.data}")
    }

    /**
     * Resolves document info (name and type) from a content:// URI.
     * Uses ContentResolver to query OpenableColumns for display name.
     */
    private fun resolveDocumentInfo(uri: Uri): DocumentInfo? {
        return try {
            var fileName: String? = null

            // Get MIME type from ContentResolver
            val mimeType = contentResolver.getType(uri)
            Log.d(TAG, "MIME type from ContentResolver: $mimeType")

            // Query for display name
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            // Fallback: extract filename from URI path
            if (fileName == null) {
                fileName = uri.lastPathSegment ?: "Unknown"
            }

            Log.d(TAG, "Resolved document: name=$fileName, mimeType=$mimeType")

            // Determine document type
            val documentType = DocumentType.detect(mimeType, fileName)

            if (documentType == DocumentType.UNKNOWN) {
                Log.w(TAG, "Unknown document type for: $fileName (mime: $mimeType)")
                Toast.makeText(this, "Unsupported file format", Toast.LENGTH_SHORT).show()
                return null
            }

            DocumentInfo(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                documentType = documentType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving document info", e)
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}

/**
 * Data class holding resolved document information.
 */
data class DocumentInfo(
    val uri: Uri,
    val fileName: String,
    val mimeType: String?,
    val documentType: DocumentType
)

/**
 * Root composable for the Reader app.
 * Manages navigation between home screen and document viewers.
 */
@Composable
fun ReaderApp(
    initialIntent: Intent?,
    onResolveUri: (Uri) -> DocumentInfo?
) {
    var currentDocument by remember { mutableStateOf<DocumentInfo?>(null) }

    // Get context and contentResolver at composition level
    val context = androidx.compose.ui.platform.LocalContext.current
    val contentResolver = context.contentResolver

    // ViewModels
    val pdfViewModel: PdfViewModel = viewModel()
    val officeViewModel: OfficeViewModel = viewModel()

    // Handle initial intent
    LaunchedEffect(initialIntent) {
        if (initialIntent?.action == Intent.ACTION_VIEW) {
            initialIntent.data?.let { uri ->
                onResolveUri(uri)?.let { docInfo ->
                    currentDocument = docInfo
                }
            }
        }
    }

    // Navigation based on current document
    when (val doc = currentDocument) {
        null -> {
            // No document selected - show home screen
            HomeScreen(
                onFileSelected = { uri ->
                    onResolveUri(uri)?.let { docInfo ->
                        currentDocument = docInfo
                    }
                }
            )
        }
        else -> {
            // Document selected - show appropriate viewer
            when (doc.documentType) {
                DocumentType.PDF -> {
                    // Open PDF in PdfViewModel
                    LaunchedEffect(doc.uri) {
                        pdfViewModel.openPdf(
                            contentResolver = contentResolver,
                            uri = doc.uri,
                            fileName = doc.fileName
                        )
                    }

                    PdfViewerScreen(
                        viewModel = pdfViewModel,
                        onNavigateBack = {
                            currentDocument = null
                            pdfViewModel.clearCache()
                        }
                    )
                }
                DocumentType.DOCX, DocumentType.XLSX, DocumentType.PPTX -> {
                    // Open Office document in OfficeViewModel
                    LaunchedEffect(doc.uri) {
                        officeViewModel.openDocument(
                            contentResolver = contentResolver,
                            uri = doc.uri,
                            fileName = doc.fileName,
                            documentType = doc.documentType
                        )
                    }

                    OfficeViewerScreen(
                        viewModel = officeViewModel,
                        onNavigateBack = {
                            currentDocument = null
                            officeViewModel.reset()
                        }
                    )
                }
                DocumentType.UNKNOWN -> {
                    // Should not happen due to validation, but handle gracefully
                    currentDocument = null
                }
            }
        }
    }
}