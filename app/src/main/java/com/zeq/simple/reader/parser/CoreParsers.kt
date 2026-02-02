package com.zeq.simple.reader.parser

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ParseResult(
    val htmlContent: String,
    val rawTextItems: List<String>
)

object CoreOfficeParser {
    private const val MAX_UNZIPPED_SIZE = 50 * 1024 * 1024L // 50MB limit

    /**
     * Extract all data from a ZIP-based Office file in a single pass.
     * Returns a map of filename -> byte content for images and XML files.
     */
    fun extractAllFromZip(
        contentResolver: ContentResolver,
        uri: Uri,
        targetPatterns: Set<String>
    ): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        var totalBytesRead = 0L

        contentResolver.openInputStream(uri)?.use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val isMatch = targetPatterns.any { pattern ->
                        when {
                            // Exact match
                            entryName.equals(pattern, ignoreCase = true) -> true
                            // Wildcard pattern like "word/media/*"
                            pattern.endsWith("/*") -> {
                                val prefix = pattern.dropLast(1) // Remove "*"
                                entryName.startsWith(prefix, ignoreCase = true)
                            }
                            // Regex pattern
                            pattern.contains("*") -> {
                                val regex = pattern.replace(".", "\\.").replace("*", ".*")
                                entryName.matches(Regex(regex, RegexOption.IGNORE_CASE))
                            }
                            else -> false
                        }
                    }

                    if (isMatch && !entry.isDirectory) {
                        val baos = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (zis.read(buffer).also { len = it } != -1) {
                            totalBytesRead += len
                            if (totalBytesRead > MAX_UNZIPPED_SIZE) {
                                throw SecurityException("File too large (Zip Bomb detected)")
                            }
                            baos.write(buffer, 0, len)
                        }
                        result[entryName] = baos.toByteArray()
                    }
                    entry = zis.nextEntry
                }
            }
        }
        return result
    }

    /**
     * Parse relationships XML to get rId -> target mappings.
     * Used for linking images in DOCX/PPTX.
     */
    fun parseRelationships(xmlBytes: ByteArray): Map<String, String> {
        val relationships = mutableMapOf<String, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlBytes.inputStream(), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (id != null && target != null) {
                        relationships[id] = target
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) { }
        return relationships
    }

    /**
     * Convert image bytes to Base64 data URI for embedding in HTML.
     */
    fun imageToDataUri(imageBytes: ByteArray, fileName: String): String {
        val mimeType = when {
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".gif", true) -> "image/gif"
            fileName.endsWith(".bmp", true) -> "image/bmp"
            fileName.endsWith(".svg", true) -> "image/svg+xml"
            fileName.endsWith(".emf", true) -> "image/emf"
            fileName.endsWith(".wmf", true) -> "image/wmf"
            else -> "image/png"
        }
        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$base64"
    }

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
