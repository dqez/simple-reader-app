package com.zeq.simple.reader.model

import android.graphics.Bitmap

/**
 * Sealed class representing UI states for document viewers.
 * Provides type-safe state handling for MVVM architecture.
 */
sealed class DocumentState {

    /** Initial state before any document is loaded */
    data object Idle : DocumentState()

    /** Document is being loaded/parsed */
    data object Loading : DocumentState()

    /** PDF document loaded successfully */
    data class PdfLoaded(
        val pageCount: Int,
        val fileName: String
    ) : DocumentState()

    /** Office document converted to HTML successfully */
    data class OfficeLoaded(
        val htmlContent: String,
        val fileName: String,
        val documentType: DocumentType
    ) : DocumentState()

    /** Error occurred during loading/parsing */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : DocumentState()
}

/**
 * Represents a single page in a PDF document.
 * Used by the PDF viewer's RecyclerView/LazyColumn.
 */
data class PdfPage(
    val pageIndex: Int,
    val bitmap: Bitmap?,
    val isLoading: Boolean = false,
    val width: Int = 0,
    val height: Int = 0
)

/**
 * Configuration for PDF rendering.
 * Controls quality vs memory tradeoff.
 */
data class PdfRenderConfig(
    val maxBitmapWidth: Int = 1080,
    val maxBitmapHeight: Int = 1920,
    val cacheSize: Int = 5,
    val renderQuality: Float = 1.5f // Scale factor for rendering
)
