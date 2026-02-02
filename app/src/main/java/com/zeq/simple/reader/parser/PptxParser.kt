package com.zeq.simple.reader.parser

import android.content.ContentResolver
import android.net.Uri
import org.xmlpull.v1.XmlPullParser

object PptxParser {
    fun parse(contentResolver: ContentResolver, uri: Uri): ParseResult {
        val sbHtml = StringBuilder()
        val textList = mutableListOf<String>()

        sbHtml.append("<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sbHtml.append("<style>body{background:#eee;padding:10px;font-family:sans-serif;} .slide{background:white;margin-bottom:16px;padding:20px;box-shadow:0 1px 3px rgba(0,0,0,0.2);} p{margin:0.5em 0;}</style>")
        sbHtml.append("</head><body>")

        // Sequential reading of slides
        CoreOfficeParser.parseZipFile(contentResolver, uri, setOf("ppt/slides/slide.*\\.xml")) { name, stream ->
            sbHtml.append("<div class='slide'>")

            val parser = CoreOfficeParser.createXmlParser(stream)
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                     if (parser.name == "a:p") {
                         sbHtml.append("<p>")
                     } else if (parser.name == "a:t") {
                         val text = parser.nextText()
                         if (!text.isNullOrEmpty()) {
                             sbHtml.append(text)
                             textList.add(text)
                         }
                     }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (parser.name == "a:p") sbHtml.append("</p>")
                }
                eventType = parser.next()
            }
            sbHtml.append("</div>")
        }

        sbHtml.append("</body></html>")
        return ParseResult(sbHtml.toString(), textList)
    }
}
