package com.zeq.simple.reader.parser

import android.util.Log
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
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
     * Processes a single paragraph, applying basic styling.
     */
    private fun processParagraph(paragraph: XWPFParagraph): String {
        val text = paragraph.text?.trim() ?: return ""
        if (text.isEmpty()) return "<p>&nbsp;</p>\n"

        val escapedText = escapeHtml(text)

        // Detect heading styles
        val style = paragraph.style?.lowercase() ?: ""
        return when {
            style.contains("heading1") || style.contains("title") ->
                "<h1>$escapedText</h1>\n"
            style.contains("heading2") ->
                "<h2>$escapedText</h2>\n"
            style.contains("heading3") ->
                "<h3>$escapedText</h3>\n"
            else -> {
                // Apply inline formatting
                val formattedText = processInlineFormatting(paragraph)
                "<p>$formattedText</p>\n"
            }
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
