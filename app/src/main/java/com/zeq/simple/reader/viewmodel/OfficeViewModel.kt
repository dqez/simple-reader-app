package com.zeq.simple.reader.viewmodel

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeq.simple.reader.model.DocumentState
import com.zeq.simple.reader.model.DocumentType
import com.zeq.simple.reader.parser.DocxParser
import com.zeq.simple.reader.parser.DocumentParseException
import com.zeq.simple.reader.parser.XlsxParser
import com.zeq.simple.reader.parser.PptxParser
import com.zeq.simple.reader.parser.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for Office document parsing (DOCX, XLSX).
 * Coordinates parsing and converts documents to HTML for WebView rendering.
 *
 * Design decisions:
 * - Parsing runs on Dispatchers.IO to avoid blocking main thread
 * - Lazy initialization of parsers to reduce startup overhead
 * - HTML content is cached in state to avoid re-parsing
 */
class OfficeViewModel : ViewModel() {

    companion object {
        private const val TAG = "OfficeViewModel"
    }

    private val _state = MutableStateFlow<DocumentState>(DocumentState.Idle)
    val state: StateFlow<DocumentState> = _state.asStateFlow()

    // Lazy parser initialization
    private val docxParser by lazy { DocxParser() }
    private val xlsxParser by lazy { XlsxParser() }

    /**
     * Opens and parses an Office document from a content:// URI.
     *
     * @param contentResolver System ContentResolver for URI access
     * @param uri Content URI pointing to the document
     * @param fileName Display name of the file
     * @param documentType Type of document (DOCX or XLSX)
     */
    fun openDocument(
        contentResolver: ContentResolver,
        uri: Uri,
        fileName: String,
        documentType: DocumentType
    ) {
        viewModelScope.launch {
            _state.value = DocumentState.Loading

            try {
                val parseResult = withContext(Dispatchers.IO) {
                    parseDocument(contentResolver, uri, documentType)
                }

                _state.value = DocumentState.OfficeLoaded(
                    htmlContent = parseResult.htmlContent,
                    fileName = fileName,
                    documentType = documentType,
                    rawTextItems = parseResult.rawTextItems
                )

            } catch (e: DocumentParseException) {
                Log.e(TAG, "Parse error", e)
                _state.value = DocumentState.Error(
                    message = e.message ?: "Failed to parse document",
                    exception = e
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                _state.value = DocumentState.Error(
                    message = "Failed to open document: ${e.message}",
                    exception = e
                )
            }
        }
    }

    /**
     * Parses document on IO dispatcher.
     * Delegates to appropriate parser using ContentResolver and Uri.
     */
    private fun parseDocument(
        contentResolver: ContentResolver,
        uri: Uri,
        documentType: DocumentType
    ): ParseResult {
        return when (documentType) {
            DocumentType.DOCX -> docxParser.parse(contentResolver, uri)
            DocumentType.XLSX -> xlsxParser.parse(contentResolver, uri)
            DocumentType.PPTX -> PptxParser.parse(contentResolver, uri)
            else -> throw DocumentParseException("Unsupported document type: $documentType")
        }
    }

    /**
     * Performs a text search on the loaded document content.
     * Updates the state with search results.
     */
    fun search(query: String) {
        val currentState = _state.value
        if (currentState is DocumentState.OfficeLoaded) {
            if (query.isBlank()) {
                _state.value = currentState.copy(
                    searchResults = emptyList(),
                    searchMatchCount = 0
                )
                return
            }

            viewModelScope.launch {
                val results = withContext(Dispatchers.Default) {
                    currentState.rawTextItems.filter {
                        it.contains(query, ignoreCase = true)
                    }
                }

                _state.value = currentState.copy(
                    searchResults = results,
                    searchMatchCount = results.size
                )
            }
        }
    }

    /**
     * Resets state to Idle.
     * Call when navigating away from the document.
     */
    fun reset() {
        _state.value = DocumentState.Idle
    }
}
