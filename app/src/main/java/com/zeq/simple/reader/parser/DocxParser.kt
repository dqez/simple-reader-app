package com.zeq.simple.reader.parser

import android.util.Base64
import android.util.Log
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFPicture
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.BufferedInputStream
import java.io.InputStream

/**
 * Parser for DOCX files using Apache POI.
 * Converts document content to minimal HTML for WebView rendering.
 *
 * Design decisions:
 * - Uses BufferedInputStream for efficient I/O
 * - Generates semantic HTML without external dependencies
 * - Strips complex formatting to minimize memory usage
 * - HTML-escapes all text content for security
 */
class DocxParser {

    companion object {
        private const val TAG = "DocxParser"
        private const val BUFFER_SIZE = 8192

        private const val HTML_HEADER = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            font-size: 16px;
            line-height: 1.6;
            color: #212121;
            padding: 16px;
            margin: 0;
            background-color: #ffffff;
            word-wrap: break-word;
            overflow-wrap: break-word;
        }
        @media (prefers-color-scheme: dark) {
            body { background-color: #121212; color: #e0e0e0; }
            table { border-color: #424242; }
            th, td { border-color: #424242; }
            th { background-color: #1e1e1e; }
        }
        h1 { font-size: 24px; margin: 24px 0 16px 0; font-weight: 600; }
        h2 { font-size: 20px; margin: 20px 0 12px 0; font-weight: 600; }
        h3 { font-size: 18px; margin: 16px 0 8px 0; font-weight: 600; }
        p { margin: 8px 0; }
        table {
            width: 100%;
            border-collapse: collapse;
            margin: 16px 0;
            font-size: 14px;
        }
        th, td {
            border: 1px solid #e0e0e0;
            padding: 8px 12px;
            text-align: left;
        }
        th { background-color: #f5f5f5; font-weight: 600; }
        strong { font-weight: 600; }
        em { font-style: italic; }
        u { text-decoration: underline; }
        img {
            max-width: 100%;
            height: auto;
            display: block;
            margin: 12px 0;
            border-radius: 4px;
        }
        .image-container {
            text-align: center;
            margin: 16px 0;
        }
        .page-break {
            border-top: 2px dashed #9e9e9e;
            margin: 24px 0;
            padding-top: 24px;
            position: relative;
        }
        .page-break::before {
            content: '--- Page Break ---';
            display: block;
            text-align: center;
            color: #757575;
            font-size: 12px;
            font-weight: 500;
            background-color: #ffffff;
            padding: 4px 12px;
            position: absolute;
            top: -10px;
            left: 50%;
            transform: translateX(-50%);
        }
        @media (prefers-color-scheme: dark) {
            .page-break {
                border-color: #616161;
            }
            .page-break::before {
                background-color: #121212;
                color: #9e9e9e;
            }
        }
    </style>
</head>
<body>
"""

        private const val HTML_FOOTER = """
</body>
</html>"""
    }

    /**
     * Parses DOCX InputStream and converts to HTML string.
     *
     * @param inputStream Raw input stream from ContentResolver
     * @return HTML string representation of the document
     * @throws DocumentParseException if parsing fails
     */
    fun parseToHtml(inputStream: InputStream): String {
        return try {
            BufferedInputStream(inputStream, BUFFER_SIZE).use { bufferedStream ->
                XWPFDocument(bufferedStream).use { document ->
                    buildHtmlFromDocument(document)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse DOCX", e)
            throw DocumentParseException("Failed to parse DOCX document: ${e.message}", e)
        }
    }

    /**
     * Builds HTML from XWPFDocument by iterating body elements.
     * Preserves document order (paragraphs and tables).
     */
    private fun buildHtmlFromDocument(document: XWPFDocument): String {
        val htmlBuilder = StringBuilder()

        // HTML header with embedded CSS for styling
        htmlBuilder.append(HTML_HEADER)

        // Process body elements in document order
        document.bodyElements.forEach { element ->
            when (element) {
                is XWPFParagraph -> {
                    htmlBuilder.append(processParagraph(element))
                }
                is XWPFTable -> {
                    htmlBuilder.append(processTable(element))
                }
            }
        }

        htmlBuilder.append(HTML_FOOTER)
        return htmlBuilder.toString()
    }

    /**
     * Processes a single paragraph, applying basic styling and extracting images.
     */
    private fun processParagraph(paragraph: XWPFParagraph): String {
        val builder = StringBuilder()

        // Check for page break before this paragraph
        if (hasPageBreakBefore(paragraph)) {
            builder.append("<div class=\"page-break\"></div>\n")
        }

        // Check for embedded images first
        val images = extractImagesFromParagraph(paragraph)
        if (images.isNotEmpty()) {
            images.forEach { imageHtml ->
                builder.append(imageHtml)
            }
            // Also include any text in the paragraph
            val text = paragraph.text?.trim() ?: ""
            if (text.isNotEmpty()) {
                val escapedText = escapeHtml(text)
                val style = paragraph.style?.lowercase() ?: ""
                when {
                    style.contains("heading1") || style.contains("title") ->
                        builder.append("<h1>$escapedText</h1>\n")
                    style.contains("heading2") ->
                        builder.append("<h2>$escapedText</h2>\n")
                    style.contains("heading3") ->
                        builder.append("<h3>$escapedText</h3>\n")
                    else -> {
                        val formattedText = processInlineFormatting(paragraph)
                        builder.append("<p>$formattedText</p>\n")
                    }
                }
            }
            return builder.toString()
        }

        val text = paragraph.text?.trim() ?: ""
        if (text.isEmpty()) {
            // Even empty paragraphs can have page breaks
            return if (builder.isNotEmpty()) builder.toString() else "<p>&nbsp;</p>\n"
        }

        val escapedText = escapeHtml(text)

        // Detect heading styles
        val style = paragraph.style?.lowercase() ?: ""
        when {
            style.contains("heading1") || style.contains("title") ->
                builder.append("<h1>$escapedText</h1>\n")
            style.contains("heading2") ->
                builder.append("<h2>$escapedText</h2>\n")
            style.contains("heading3") ->
                builder.append("<h3>$escapedText</h3>\n")
            else -> {
                // Apply inline formatting
                val formattedText = processInlineFormatting(paragraph)
                builder.append("<p>$formattedText</p>\n")
            }
        }

        return builder.toString()
    }

    /**
     * Checks if a paragraph has a page break before it.
     */
    private fun hasPageBreakBefore(paragraph: XWPFParagraph): Boolean {
        try {
            // Check paragraph properties for page break
            val ctp = paragraph.ctp
            if (ctp != null && ctp.pPr != null) {
                val pageBreakBefore = ctp.pPr.pageBreakBefore
                if (pageBreakBefore != null) {
                    try {
                        // Try to get boolean value
                        val isPageBreak = pageBreakBefore.`val` == true
                        if (isPageBreak) return true
                    } catch (e: Exception) {
                        // If val property doesn't work, presence of pageBreakBefore element itself indicates true
                        return true
                    }
                }
            }

            // Check runs for explicit page break
            paragraph.runs?.forEach { run ->
                try {
                    val ctr = run.ctr
                    if (ctr != null && ctr.brList != null) {
                        for (br in ctr.brList) {
                            if (br != null && br.type != null) {
                                val brType = br.type.toString()
                                if (brType == "PAGE" || brType.contains("page", ignoreCase = true)) {
                                    return true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking run for page break", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking paragraph for page break", e)
        }

        return false
    }

    /**
     * Extracts images from paragraph runs and converts to base64 HTML img tags.
     */
    private fun extractImagesFromParagraph(paragraph: XWPFParagraph): List<String> {
        val images = mutableListOf<String>()

        try {
            paragraph.runs.forEach { run ->
                val embeddedPictures = run.embeddedPictures
                embeddedPictures?.forEach { picture ->
                    try {
                        val imageHtml = convertPictureToHtml(picture)
                        if (imageHtml != null) {
                            images.add(imageHtml)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to process embedded picture", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract images from paragraph", e)
        }

        return images
    }

    /**
     * Converts XWPFPicture to HTML img tag with base64 encoded data.
     */
    private fun convertPictureToHtml(picture: XWPFPicture): String? {
        try {
            val pictureData = picture.pictureData ?: return null
            val imageBytes = pictureData.data ?: return null

            // Convert to base64
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // Get MIME type from extension
            val extension = pictureData.suggestFileExtension() ?: "png"
            val mimeType = when (extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                "webp" -> "image/webp"
                else -> "image/png"
            }

            // Get dimensions if available
            val width = picture.width
            val height = picture.depth

            val dimensionAttr = if (width > 0 && height > 0) {
                " style=\"max-width: 100%; width: ${width}px; height: auto;\""
            } else {
                ""
            }

            return "<div class=\"image-container\"><img src=\"data:$mimeType;base64,$base64Image\"$dimensionAttr alt=\"Embedded image\" /></div>\n"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert picture to HTML", e)
            return null
        }
    }

    /**
     * Processes inline formatting (bold, italic, underline) within a paragraph.
     */
    private fun processInlineFormatting(paragraph: XWPFParagraph): String {
        val builder = StringBuilder()

        paragraph.runs.forEach { run ->
            var text = escapeHtml(run.text() ?: "")

            // Apply formatting tags
            if (run.isBold) text = "<strong>$text</strong>"
            if (run.isItalic) text = "<em>$text</em>"
            if (run.underline != UnderlinePatterns.NONE) {
                text = "<u>$text</u>"
            }

            builder.append(text)
        }

        return builder.toString().ifEmpty { escapeHtml(paragraph.text ?: "") }
    }

    /**
     * Processes a table element into HTML table.
     */
    private fun processTable(table: XWPFTable): String {
        val builder = StringBuilder()
        builder.append("<table>\n")

        table.rows.forEachIndexed { rowIndex, row ->
            builder.append("<tr>\n")
            row.tableCells.forEach { cell ->
                val tag = if (rowIndex == 0) "th" else "td"
                val cellText = escapeHtml(cell.text ?: "")
                builder.append("<$tag>$cellText</$tag>\n")
            }
            builder.append("</tr>\n")
        }

        builder.append("</table>\n")
        return builder.toString()
    }

    /**
     * Escapes HTML special characters to prevent XSS and rendering issues.
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

/**
 * Custom exception for document parsing errors.
 */
class DocumentParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
