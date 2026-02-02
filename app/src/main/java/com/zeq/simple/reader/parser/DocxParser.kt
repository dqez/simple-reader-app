package com.zeq.simple.reader.parser

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

class DocxParser {

    /**
     * Parses DOCX Uri and converts to HTML string.
     * Supports: Images, Tables, Page Breaks, Lists, Headings, Text Formatting.
     */
    fun parse(contentResolver: ContentResolver, uri: Uri): ParseResult {
        val sbHtml = StringBuilder()
        val textList = mutableListOf<String>()

        // Step 1: Extract all needed files from ZIP
        val zipContents = CoreOfficeParser.extractAllFromZip(
            contentResolver, uri,
            setOf(
                "word/document.xml",
                "word/_rels/document.xml.rels",
                "word/media/*"
            )
        )

        // Step 2: Parse relationships to map rId -> image path
        val relsBytes = zipContents["word/_rels/document.xml.rels"]
        val relationships = if (relsBytes != null) {
            CoreOfficeParser.parseRelationships(relsBytes)
        } else {
            emptyMap()
        }

        // Step 3: Build image map (rId -> base64 data URI)
        // Relationships target can be "media/image1.png" or "../media/image1.png"
        val imageMap = mutableMapOf<String, String>()
        relationships.forEach { (rId, target) ->
            // Normalize target path
            val normalizedTarget = target
                .removePrefix("../")
                .removePrefix("./")

            if (normalizedTarget.startsWith("media/") || target.contains("media/")) {
                // Try multiple path variations to find the image
                val possiblePaths = listOf(
                    "word/$normalizedTarget",
                    "word/media/${normalizedTarget.substringAfterLast("/")}",
                    normalizedTarget
                )

                for (path in possiblePaths) {
                    val imageBytes = zipContents[path]
                    if (imageBytes != null) {
                        imageMap[rId] = CoreOfficeParser.imageToDataUri(imageBytes, path)
                        break
                    }
                }

                // If still not found, try to find by filename in any extracted media
                if (!imageMap.containsKey(rId)) {
                    val fileName = normalizedTarget.substringAfterLast("/")
                    val matchingEntry = zipContents.entries.find {
                        it.key.endsWith(fileName, ignoreCase = true)
                    }
                    if (matchingEntry != null) {
                        imageMap[rId] = CoreOfficeParser.imageToDataUri(matchingEntry.value, fileName)
                    }
                }
            }
        }

        // CSS
        val css = """
            <style>
                * { box-sizing: border-box; }
                body { font-family: 'Calibri', 'Segoe UI', Roboto, sans-serif; line-height: 1.6; padding: 0; margin: 0; color: #333; background: #e8e8e8; }
                
                .controls { position: sticky; top: 0; background: #fff; border-bottom: 2px solid #ddd; padding: 8px; display: flex; align-items: center; gap: 8px; z-index: 1000; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                .search-box { flex-grow: 1; padding: 10px; border: 1px solid #ccc; border-radius: 4px; font-size: 14px; }
                .nav-btn { padding: 10px 14px; background: #f5f5f5; border: 1px solid #ccc; border-radius: 4px; cursor: pointer; font-size: 16px; }
                .nav-btn:active { background: #e0e0e0; }
                
                #content { 
                    padding: 50px 60px; 
                    max-width: 900px; 
                    margin: 20px auto; 
                    background: white; 
                    min-height: 100vh; 
                    box-shadow: 0 0 20px rgba(0,0,0,0.15);
                }
                
                p { margin: 12px 0; min-height: 1em; }
                
                /* Responsive Tables */
                .table-wrapper { overflow-x: auto; margin: 16px 0; -webkit-overflow-scrolling: touch; }
                table { border-collapse: collapse; width: 100%; min-width: 400px; }
                td, th { border: 1px solid #bbb; padding: 8px 12px; vertical-align: top; }
                th { background: #f0f0f0; font-weight: bold; }
                
                /* Headings */
                h1 { font-size: 26px; font-weight: bold; margin: 28px 0 16px 0; color: #1a1a1a; border-bottom: 3px solid #3498db; padding-bottom: 10px; }
                h2 { font-size: 22px; font-weight: bold; margin: 24px 0 12px 0; color: #2c3e50; }
                h3 { font-size: 18px; font-weight: bold; margin: 20px 0 10px 0; color: #34495e; }
                
                /* Lists */
                ul, ol { margin: 12px 0; padding-left: 30px; }
                li { margin: 6px 0; }
                
                /* Images */
                img { max-width: 100%; height: auto; display: block; margin: 16px auto; border-radius: 4px; }
                
                /* Page Break */
                .page-break { 
                    page-break-after: always; 
                    border-top: 2px dashed #ccc; 
                    margin: 40px 0; 
                    position: relative;
                }
                .page-break::after {
                    content: "Page Break";
                    position: absolute;
                    top: -12px;
                    left: 50%;
                    transform: translateX(-50%);
                    background: #e8e8e8;
                    padding: 0 10px;
                    color: #888;
                    font-size: 12px;
                }
                
                /* Alignment */
                .text-center { text-align: center; }
                .text-right { text-align: right; }
                .text-justify { text-align: justify; }
                
                /* Search Highlight */
                mark.highlight { background-color: #fff59d; color: black; padding: 2px; border-radius: 2px; }
                mark.highlight.active { background-color: #ff9800; }
            </style>
        """

        // JavaScript for Search
        val script = """
            <script>
                var matches = [];
                var currentIndex = -1;

                function performSearch() {
                    var query = document.getElementById("searchInput").value.toLowerCase();
                    var content = document.getElementById("content");
                    
                    document.querySelectorAll("mark.highlight").forEach(function(m) {
                        var parent = m.parentNode;
                        parent.replaceChild(document.createTextNode(m.innerText), m);
                        parent.normalize();
                    });
                    matches = [];
                    currentIndex = -1;

                    if (!query) { document.getElementById("searchStatus").innerText = ""; return; }

                    var walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT, null, false);
                    var textNodes = [];
                    var node;
                    while(node = walker.nextNode()) textNodes.push(node);

                    textNodes.forEach(function(node) {
                        var text = node.nodeValue;
                        var idx = text.toLowerCase().indexOf(query);
                        if (idx >= 0) {
                            var range = document.createRange();
                            range.setStart(node, idx);
                            range.setEnd(node, idx + query.length);
                            var mark = document.createElement("mark");
                            mark.className = "highlight";
                            range.surroundContents(mark);
                            matches.push(mark);
                        }
                    });

                    if (matches.length > 0) { nextMatch(); }
                    else { document.getElementById("searchStatus").innerText = "0/0"; }
                }

                function nextMatch() {
                    if (matches.length === 0) return;
                    if (currentIndex >= 0) matches[currentIndex].classList.remove("active");
                    currentIndex = (currentIndex + 1) % matches.length;
                    var target = matches[currentIndex];
                    target.classList.add("active");
                    target.scrollIntoView({behavior: "smooth", block: "center"});
                    document.getElementById("searchStatus").innerText = (currentIndex + 1) + "/" + matches.length;
                }
            </script>
        """

        sbHtml.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sbHtml.append(css)
        sbHtml.append(script)
        sbHtml.append("</head><body>")

        // Search Controls
        sbHtml.append("""
            <div class="controls">
                <input type="text" id="searchInput" class="search-box" placeholder="Search document..." onkeyup="if(event.key === 'Enter') performSearch()">
                <button class="nav-btn" onclick="performSearch()">🔍</button>
                <button class="nav-btn" onclick="nextMatch()">▼</button>
                <span id="searchStatus" style="font-size: 14px; min-width: 50px; text-align: center;"></span>
            </div>
            <div id="content">
        """.trimIndent())

        // Step 4: Parse document.xml
        val docBytes = zipContents["word/document.xml"]
        if (docBytes != null) {
            parseDocumentXml(docBytes, sbHtml, textList, imageMap)
        }

        sbHtml.append("</div></body></html>")
        return ParseResult(sbHtml.toString(), textList)
    }

    private fun parseDocumentXml(
        docBytes: ByteArray,
        sbHtml: StringBuilder,
        textList: MutableList<String>,
        imageMap: Map<String, String>
    ) {
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(docBytes.inputStream(), "UTF-8")

            var eventType = parser.eventType

            // State
            val paragraphContent = StringBuilder()
            var pStyle = ""
            var pAlign = ""
            var isBold = false
            var isItalic = false
            var isUnderline = false
            var runColor = ""
            var inList = false
            var listType = "ul" // ul or ol
            var currentImageRId: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            // Table
                            "w:tbl" -> sbHtml.append("<div class=\"table-wrapper\"><table>")
                            "w:tr" -> sbHtml.append("<tr>")
                            "w:tc" -> sbHtml.append("<td>")

                            // Paragraph
                            "w:p" -> {
                                paragraphContent.setLength(0)
                                pStyle = ""
                                pAlign = ""
                            }
                            "w:pStyle" -> pStyle = parser.getAttributeValue(null, "w:val") ?: ""
                            "w:jc" -> pAlign = parser.getAttributeValue(null, "w:val") ?: ""

                            // List detection
                            "w:numPr" -> inList = true
                            "w:ilvl" -> { /* List level - could be used for nested lists */ }

                            // Run (text segment)
                            "w:r" -> {
                                isBold = false
                                isItalic = false
                                isUnderline = false
                                runColor = ""
                            }
                            "w:b" -> isBold = true
                            "w:i" -> isItalic = true
                            "w:u" -> isUnderline = true
                            "w:color" -> {
                                val colorVal = parser.getAttributeValue(null, "w:val")
                                if (colorVal != null && colorVal != "auto") runColor = "#$colorVal"
                            }

                            // Text
                            "w:t" -> {
                                val text = parser.nextText()
                                if (!text.isNullOrEmpty()) {
                                    paragraphContent.append(formatRun(text, isBold, isItalic, isUnderline, runColor))
                                    textList.add(text)
                                }
                            }

                            // Line break
                            "w:br" -> {
                                val brType = parser.getAttributeValue(null, "w:type")
                                if (brType == "page") {
                                    paragraphContent.append("</p><div class=\"page-break\"></div><p>")
                                } else {
                                    paragraphContent.append("<br/>")
                                }
                            }

                            // Image reference
                            "a:blip" -> {
                                currentImageRId = parser.getAttributeValue(null, "r:embed")
                                    ?: parser.getAttributeValue(null, "embed")
                            }

                            // Drawing/Picture container - render image here
                            "w:drawing", "w:pict" -> { /* Container for images */ }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (name) {
                            "w:p" -> {
                                val content = paragraphContent.toString()
                                if (inList) {
                                    sbHtml.append("<li>$content</li>\n")
                                    inList = false
                                } else {
                                    sbHtml.append(renderParagraph(content, pStyle, pAlign))
                                }
                            }
                            "w:tbl" -> sbHtml.append("</table></div>")
                            "w:tr" -> sbHtml.append("</tr>")
                            "w:tc" -> sbHtml.append("</td>")

                            // Image end - render if we have rId
                            "w:drawing", "w:pict", "pic:pic", "a:blip" -> {
                                if (currentImageRId != null) {
                                    val dataUri = imageMap[currentImageRId]
                                    if (dataUri != null) {
                                        paragraphContent.append("<img src=\"$dataUri\" alt=\"Image\"/>")
                                    }
                                    currentImageRId = null
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            throw DocumentParseException("Failed to parse DOCX content", e)
        }
    }

    private fun renderParagraph(content: String, style: String, align: String): String {
        val tag = when {
            style.contains("Heading1", true) || style.contains("Title", true) -> "h1"
            style.contains("Heading2", true) -> "h2"
            style.contains("Heading3", true) -> "h3"
            else -> "p"
        }

        val alignClass = when(align) {
            "center" -> "text-center"
            "right" -> "text-right"
            "both" -> "text-justify"
            else -> ""
        }

        val classAttr = if (alignClass.isNotEmpty()) " class=\"$alignClass\"" else ""
        val safeContent = if (content.isEmpty()) "&nbsp;" else content

        return "<$tag$classAttr>$safeContent</$tag>\n"
    }

    private fun formatRun(text: String, b: Boolean, i: Boolean, u: Boolean, color: String): String {
        var res = escapeHtml(text)
        if (b) res = "<b>$res</b>"
        if (i) res = "<i>$res</i>"
        if (u) res = "<u>$res</u>"
        if (color.isNotEmpty()) res = "<span style=\"color:$color\">$res</span>"
        return res
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
