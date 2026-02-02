package com.zeq.simple.reader.viewmodel

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeq.simple.reader.model.DocumentState
import com.zeq.simple.reader.model.PdfPage
import com.zeq.simple.reader.model.PdfRenderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * ViewModel for PDF document rendering using Android's native PdfRenderer.
 *
 * Design decisions:
 * - Uses LruCache for bitmap caching to manage memory efficiently
 * - Renders pages on-demand with coroutines for non-blocking UI
 * - Mutex ensures thread-safe access to PdfRenderer (single-threaded requirement)
 * - Properly manages ParcelFileDescriptor lifecycle
 */
class PdfViewModel : ViewModel() {

    companion object {
        private const val TAG = "PdfViewModel"
    }

    private val _state = MutableStateFlow<DocumentState>(DocumentState.Idle)
    val state: StateFlow<DocumentState> = _state.asStateFlow()

    private val _pages = MutableStateFlow<List<PdfPage>>(emptyList())
    val pages: StateFlow<List<PdfPage>> = _pages.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private val renderMutex = Mutex()

    private val config = PdfRenderConfig()

    // LruCache for bitmap caching - max memory = 1/8 of available heap
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: Int,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted && !oldValue.isRecycled) {
                // Don't recycle immediately, let GC handle it
                Log.d(TAG, "Evicted bitmap for page $key from cache")
            }
        }
    }

    /**
     * Opens a PDF document from a content:// URI.
     *
     * @param contentResolver System ContentResolver for URI access
     * @param uri Content URI pointing to the PDF file
     * @param fileName Display name of the file
     */
    fun openPdf(contentResolver: ContentResolver, uri: Uri, fileName: String) {
        viewModelScope.launch {
            _state.value = DocumentState.Loading

            try {
                withContext(Dispatchers.IO) {
                    // Close any existing document
                    closeInternal()

                    // Open file descriptor from content URI
                    fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                        ?: throw IOException("Cannot open file descriptor for URI: $uri")

                    // Initialize PdfRenderer
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                }

                val pageCount = pdfRenderer?.pageCount ?: 0

                if (pageCount == 0) {
                    _state.value = DocumentState.Error("PDF has no pages")
                    return@launch
                }

                // Initialize page list with placeholders
                _pages.value = List(pageCount) { index ->
                    PdfPage(pageIndex = index, bitmap = null, isLoading = false)
                }

                _state.value = DocumentState.PdfLoaded(
                    pageCount = pageCount,
                    fileName = fileName
                )

                // Pre-render first few pages
                preloadPages(0, minOf(config.cacheSize, pageCount))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to open PDF", e)
                _state.value = DocumentState.Error(
                    message = "Failed to open PDF: ${e.message}",
                    exception = e
                )
            }
        }
    }

    /**
     * Renders a specific page and returns its bitmap.
     * Uses caching to avoid re-rendering.
     *
     * @param pageIndex Zero-based page index
     * @param screenWidth Target width for scaling
     */
    fun renderPage(pageIndex: Int, screenWidth: Int) {
        viewModelScope.launch {
            val renderer = pdfRenderer ?: return@launch
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@launch

            // Check cache first
            bitmapCache.get(pageIndex)?.let { cachedBitmap ->
                if (!cachedBitmap.isRecycled) {
                    updatePageBitmap(pageIndex, cachedBitmap)
                    return@launch
                }
            }

            // Mark page as loading
            updatePageLoading(pageIndex, true)

            try {
                val bitmap = withContext(Dispatchers.IO) {
                    renderMutex.withLock {
                        renderPageInternal(pageIndex, screenWidth)
                    }
                }

                bitmap?.let {
                    bitmapCache.put(pageIndex, it)
                    updatePageBitmap(pageIndex, it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to render page $pageIndex", e)
                updatePageLoading(pageIndex, false)
            }
        }
    }

    /**
     * Internal page rendering - must be called within renderMutex.
     */
    private fun renderPageInternal(pageIndex: Int, screenWidth: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null

        return renderer.openPage(pageIndex).use { page ->
            // Calculate scaled dimensions maintaining aspect ratio
            val scale = (screenWidth.toFloat() / page.width) * config.renderQuality
            val bitmapWidth = (page.width * scale).toInt()
                .coerceAtMost(config.maxBitmapWidth)
            val bitmapHeight = (page.height * scale).toInt()
                .coerceAtMost(config.maxBitmapHeight)

            // Create bitmap and render
            val bitmap = Bitmap.createBitmap(
                bitmapWidth,
                bitmapHeight,
                Bitmap.Config.ARGB_8888
            )

            // White background
            bitmap.eraseColor(android.graphics.Color.WHITE)

            page.render(
                bitmap,
                null, // Full page
                null, // No transformation matrix
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            bitmap
        }
    }

    /**
     * Preloads pages around the current visible page.
     */
    private fun preloadPages(startIndex: Int, count: Int) {
        viewModelScope.launch {
            for (i in startIndex until (startIndex + count)) {
                if (bitmapCache.get(i) == null) {
                    renderPage(i, config.maxBitmapWidth)
                }
            }
        }
    }

    /**
     * Called when user scrolls to update current page and trigger preloading.
     */
    fun onPageVisible(pageIndex: Int, screenWidth: Int) {
        _currentPage.value = pageIndex

        // Render current page if not cached
        renderPage(pageIndex, screenWidth)

        // Preload adjacent pages
        val pageCount = pdfRenderer?.pageCount ?: return
        val preloadRange = 2

        viewModelScope.launch {
            for (i in maxOf(0, pageIndex - preloadRange)..minOf(pageCount - 1, pageIndex + preloadRange)) {
                if (i != pageIndex && bitmapCache.get(i) == null) {
                    renderPage(i, screenWidth)
                }
            }
        }
    }

    /**
     * Gets the dimensions of a specific page.
     */
    fun getPageDimensions(pageIndex: Int): Pair<Int, Int>? {
        val renderer = pdfRenderer ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

        return try {
            renderer.openPage(pageIndex).use { page ->
                Pair(page.width, page.height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get page dimensions", e)
            null
        }
    }

    private fun updatePageBitmap(pageIndex: Int, bitmap: Bitmap) {
        _pages.value = _pages.value.mapIndexed { index, page ->
            if (index == pageIndex) {
                page.copy(
                    bitmap = bitmap,
                    isLoading = false,
                    width = bitmap.width,
                    height = bitmap.height
                )
            } else page
        }
    }

    private fun updatePageLoading(pageIndex: Int, isLoading: Boolean) {
        _pages.value = _pages.value.mapIndexed { index, page ->
            if (index == pageIndex) page.copy(isLoading = isLoading) else page
        }
    }

    /**
     * Clears bitmap cache to free memory.
     */
    fun clearCache() {
        bitmapCache.evictAll()
    }

    private fun closeInternal() {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing PDF resources", e)
        } finally {
            pdfRenderer = null
            fileDescriptor = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearCache()
        closeInternal()
    }
}
