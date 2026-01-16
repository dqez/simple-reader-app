package com.example.simplereader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.simplereader.ui.DocumentViewerActivity
import com.rajat.pdfviewer.PdfViewerActivity

class MainActivity : AppCompatActivity() {

    // Fix for Apache POI on Android (missing javax.xml.stream)
    companion object {
        init {
            System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLInputFactory",
                "com.ctc.wstx.stax.WstxInputFactory"
            )
            System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLOutputFactory",
                "com.ctc.wstx.stax.WstxOutputFactory"
            )
            System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLEventFactory",
                "com.ctc.wstx.stax.WstxEventFactory"
            )
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { handleDocument(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup UI
        findViewById<Button>(R.id.btnOpenFile).setOnClickListener {
            openFilePicker()
        }

        // Handle incoming intent (e.g., "Open with" from file manager)
        intent?.data?.let {
            handleDocument(it)
        }
    }

    private fun openFilePicker() {
        try {
            filePickerLauncher.launch(arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",      // .xlsx
                "application/msword",                                                     // .doc
                "application/vnd.ms-excel"                                                // .xls
            ))
        } catch (e: Exception) {
            Toast.makeText(this, "Không tìm thấy trình quản lý file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDocument(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: getNameBasedMimeType(uri.toString())

        try {
            when {
                mimeType.contains("pdf", true) -> {
                    launchPdfViewer(uri)
                }
                mimeType.contains("word", true) || 
                mimeType.contains("document", true) ||
                uri.toString().endsWith(".docx", true) -> {
                    launchDocumentViewer(uri, "docx")
                }
                mimeType.contains("sheet", true) || 
                mimeType.contains("excel", true) ||
                uri.toString().endsWith(".xlsx", true) -> {
                    launchDocumentViewer(uri, "xlsx")
                }
                else -> {
                    Toast.makeText(this, "Định dạng chưa được hỗ trợ: $mimeType", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun launchPdfViewer(uri: Uri) {
        PdfViewerActivity.launchPdfFromPath(
            context = this,
            path = uri.toString(),
            pdfTitle = "Tài liệu PDF",
            saveTo = com.rajat.pdfviewer.util.saveTo.ASK_EVERYTIME,
            fromAssets = false
        )
    }

    private fun launchDocumentViewer(uri: Uri, type: String) {
        val intent = Intent(this, DocumentViewerActivity::class.java).apply {
            data = uri
            putExtra("type", type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun getNameBasedMimeType(url: String): String {
        return when {
            url.endsWith(".pdf", true) -> "application/pdf"
            url.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            url.endsWith(".xlsx", true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> ""
        }
    }
}
