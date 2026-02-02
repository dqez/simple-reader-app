package com.zeq.simple.reader.parser

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ParseResult(
    val htmlContent: String,
    val rawTextItems: List<String>
)

object CoreOfficeParser {
    private const val MAX_UNZIPPED_SIZE = 50 * 1024 * 1024L // 50MB limit

    fun parseZipFile(
        contentResolver: ContentResolver,
        uri: Uri,
        targetFiles: Set<String>,
        onFileFound: (String, InputStream) -> Unit
    ) {
        var totalBytesRead = 0L
        contentResolver.openInputStream(uri)?.use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val isMatch = targetFiles.any { target ->
                        entryName.equals(target, ignoreCase = true) ||
                        (target.contains("*") && entryName.matches(Regex(target)))
                    }

                    if (isMatch) {
                        val noCloseStream = object : InputStream() {
                            override fun read(): Int {
                                checkSize()
                                return zis.read()
                            }
                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                checkSize()
                                return zis.read(b, off, len)
                            }
                            private fun checkSize() {
                                totalBytesRead++
                                if (totalBytesRead > MAX_UNZIPPED_SIZE) {
                                    throw SecurityException("File too large (Zip Bomb detected)")
                                }
                            }
                            override fun close() { /* Do not close underlying stream */ }
                        }
                        onFileFound(entryName, noCloseStream)
                    }
                    entry = zis.nextEntry
                }
            }
        }
    }

    fun createXmlParser(inputStream: InputStream): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        return parser
    }
}
