package com.zeq.simple.reader.parser

import android.content.ContentResolver
import android.net.Uri
import org.xmlpull.v1.XmlPullParser

class DocxParser {
    companion object {
        private const val TAG = "DocxParser"
    }

    /**
     * Parses DOCX Uri and converts to HTML string using streaming XML parsing.
     * Zero-dependency implementation removing Apache POI.
     */
    fun parse(contentResolver: ContentResolver, uri: Uri): ParseResult {
        val sbHtml = StringBuilder()
        val textList = mutableListOf<String>()

        // Basic styling
        sbHtml.append("<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sbHtml.append("<style>body{font-family:sans-serif;line-height:1.6;padding:16px;color:#212121;} p{margin:8px 0;} h1,h2,h3{color:#000;}</style>")
        sbHtml.append("</head><body>")

        try {
            CoreOfficeParser.parseZipFile(contentResolver, uri, setOf("word/document.xml")) { _, inputStream ->
                val parser = CoreOfficeParser.createXmlParser(inputStream)
                var eventType = parser.eventType

                val currentParagraph = StringBuilder()
                var isBold = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (name == "w:p" || name == "p") {
                                currentParagraph.setLength(0)
                                sbHtml.append("<p>")
                            } else if (name == "w:r" || name == "r") {
                                isBold = false
                            } else if (name == "w:b" || name == "b") {
                                isBold = true
                            } else if (name == "w:t" || name == "t") {
                                val text = parser.nextText()
                                if (!text.isNullOrEmpty()) {
                                    currentParagraph.append(text)
                                    val safeText = escapeHtml(text)
                                    if (isBold) {
                                        sbHtml.append("<b>").append(safeText).append("</b>")
                                    } else {
                                        sbHtml.append(safeText)
                                    }
                                }
                            } else if (name == "w:br" || name == "br") {
                                sbHtml.append("<br/>")
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (name == "w:p" || name == "p") {
                                sbHtml.append("</p>\n")
                                if (currentParagraph.isNotEmpty()) {
                                    textList.add(currentParagraph.toString())
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            throw DocumentParseException("Failed to parse DOCX", e)
        }

        sbHtml.append("</body></html>")
        return ParseResult(sbHtml.toString(), textList)
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
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
