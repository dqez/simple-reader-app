package com.example.simplereader.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.simplereader.R
import com.example.simplereader.parser.DocxParser
import com.example.simplereader.parser.XlsxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class DocumentViewerActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_viewer)
        
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        
        setupWebView()
        
        val uri = intent.data
        val type = intent.getStringExtra("type")
        
        if (uri != null && type != null) {
            loadDocument(uri, type)
        } else {
            finish()
        }
    }
    
    private fun setupWebView() {
        webView.settings.apply {
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            javaScriptEnabled = false // Not needed for static HTML
        }
    }
    
    private fun loadDocument(uri: Uri, type: String) {
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open file")
                
                val html = when (type) {
                    "docx" -> DocxParser().parseToHtml(inputStream)
                    "xlsx" -> XlsxParser().parseToHtml(inputStream)
                    else -> throw IllegalArgumentException("Unsupported type")
                }
                
                withContext(Dispatchers.Main) {
                    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val errorHtml = "<html><body><h3>Lỗi khi mở file:</h3><p>${e.message}</p></body></html>"
                    webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
                    Toast.makeText(this@DocumentViewerActivity, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
