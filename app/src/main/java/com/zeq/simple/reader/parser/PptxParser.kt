package com.zeq.simple.reader.parser

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

object PptxParser {

    fun parse(contentResolver: ContentResolver, uri: Uri): ParseResult {
        val sbHtml = StringBuilder()
        val textList = mutableListOf<String>()

        // Step 1: Extract all needed files
        val zipContents = CoreOfficeParser.extractAllFromZip(
            contentResolver, uri,
            setOf(
                "ppt/slides/slide*.xml",
                "ppt/slides/_rels/slide*.xml.rels",
                "ppt/media/*"
            )
        )

        // Step 2: Build global image map from all slide relationships
        val globalImageMap = mutableMapOf<String, String>()
        zipContents.keys.filter { it.contains("_rels") && it.endsWith(".rels") }.forEach { relsPath ->
            val relsBytes = zipContents[relsPath]
            if (relsBytes != null) {
                val rels = CoreOfficeParser.parseRelationships(relsBytes)
                rels.forEach { (rId, target) ->
                    if (target.contains("media/")) {
                        val imagePath = "ppt/media/" + target.substringAfterLast("/")
                        val imageBytes = zipContents.entries.find { it.key.endsWith(target.substringAfterLast("/")) }?.value
                        if (imageBytes != null) {
                            globalImageMap["$relsPath:$rId"] = CoreOfficeParser.imageToDataUri(imageBytes, target)
                        }
                    }
                }
            }
        }

        // CSS for PowerPoint-like appearance
        val css = """
            <style>
                * { box-sizing: border-box; }
                body { background: #2d2d2d; padding: 0; margin: 0; font-family: 'Segoe UI', Roboto, sans-serif; }
                
                .controls { position: sticky; top: 0; background: #1a1a1a; border-bottom: 2px solid #444; padding: 10px; display: flex; align-items: center; gap: 8px; z-index: 1000; }
                .search-box { flex-grow: 1; padding: 10px; border: 1px solid #555; border-radius: 4px; background: #333; color: white; font-size: 14px; }
                .search-box::placeholder { color: #888; }
                .nav-btn { padding: 10px 14px; background: #444; border: 1px solid #555; border-radius: 4px; cursor: pointer; color: white; font-size: 16px; }
                .nav-btn:active { background: #555; }
                
                #content { padding: 20px; display: flex; flex-direction: column; align-items: center; gap: 24px; }
                
                .slide-container { width: 100%; max-width: 960px; }
                
                .slide-number { color: #888; font-size: 12px; margin-bottom: 8px; text-align: center; }
                
                .slide { 
                    background: white; 
                    aspect-ratio: 16/9;
                    padding: 40px 50px;
                    box-shadow: 0 4px 20px rgba(0,0,0,0.4);
                    border-radius: 4px;
                    overflow: hidden;
                    display: flex;
                    flex-direction: column;
                }
                
                .slide-title { 
                    font-size: 28px; 
                    font-weight: bold; 
                    color: #1a365d; 
                    margin-bottom: 20px;
                    text-align: center;
                }
                
                .slide-content { 
                    flex: 1;
                    font-size: 18px; 
                    color: #333; 
                    line-height: 1.6;
                }
                
                .slide-content p { margin: 12px 0; }
                .slide-content ul { margin: 10px 0; padding-left: 30px; }
                .slide-content li { margin: 8px 0; }
                
                .slide-content img { 
                    max-width: 100%; 
                    max-height: 300px; 
                    object-fit: contain;
                    display: block;
                    margin: 16px auto;
                    border-radius: 4px;
                }
                
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

        sbHtml.append("""
            <div class="controls">
                <input type="text" id="searchInput" class="search-box" placeholder="Search slides..." onkeyup="if(event.key === 'Enter') performSearch()">
                <button class="nav-btn" onclick="performSearch()">🔍</button>
                <button class="nav-btn" onclick="nextMatch()">▼</button>
                <span id="searchStatus" style="font-size: 14px; min-width: 50px; text-align: center; color: #888;"></span>
            </div>
            <div id="content">
        """.trimIndent())

        // Step 3: Parse each slide
        val slideFiles = zipContents.keys
            .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy {
                val num = it.replace(Regex("[^0-9]"), "")
                num.toIntOrNull() ?: 0
            }

        slideFiles.forEachIndexed { index, slidePath ->
            val slideBytes = zipContents[slidePath] ?: return@forEachIndexed
            val slideNum = index + 1

            // Find corresponding rels file
            val relsPath = slidePath.replace("slides/", "slides/_rels/") + ".rels"
            val slideRels = zipContents[relsPath]?.let { CoreOfficeParser.parseRelationships(it) } ?: emptyMap()

            // Build slide-specific image map
            val slideImageMap = mutableMapOf<String, String>()
            slideRels.forEach { (rId, target) ->
                if (target.contains("media/")) {
                    val imageName = target.substringAfterLast("/")
                    val imageBytes = zipContents.entries.find { it.key.endsWith(imageName) }?.value
                    if (imageBytes != null) {
                        slideImageMap[rId] = CoreOfficeParser.imageToDataUri(imageBytes, target)
                    }
                }
            }

            sbHtml.append("<div class=\"slide-container\">")
            sbHtml.append("<div class=\"slide-number\">Slide $slideNum</div>")
            sbHtml.append("<div class=\"slide\">")

            parseSlideXml(slideBytes, sbHtml, textList, slideImageMap)

            sbHtml.append("</div></div>")
        }

        sbHtml.append("</div></body></html>")
        return ParseResult(sbHtml.toString(), textList)
    }

    private fun parseSlideXml(
        slideBytes: ByteArray,
        sbHtml: StringBuilder,
        textList: MutableList<String>,
        imageMap: Map<String, String>
    ) {
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(slideBytes.inputStream(), "UTF-8")

            var eventType = parser.eventType
            val paragraphContent = StringBuilder()
            var isFirstParagraph = true
            var currentImageRId: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            "a:p" -> paragraphContent.setLength(0)
                            "a:t" -> {
                                val text = parser.nextText()
                                if (!text.isNullOrEmpty()) {
                                    paragraphContent.append(escapeHtml(text))
                                    textList.add(text)
                                }
                            }
                            "a:blip" -> {
                                currentImageRId = parser.getAttributeValue(null, "r:embed")
                                    ?: parser.getAttributeValue(null, "embed")
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (name) {
                            "a:p" -> {
                                val content = paragraphContent.toString().trim()
                                if (content.isNotEmpty()) {
                                    if (isFirstParagraph) {
                                        sbHtml.append("<div class=\"slide-title\">$content</div>")
                                        sbHtml.append("<div class=\"slide-content\">")
                                        isFirstParagraph = false
                                    } else {
                                        sbHtml.append("<p>$content</p>")
                                    }
                                }
                            }
                            "p:pic", "a:blip" -> {
                                if (currentImageRId != null) {
                                    val dataUri = imageMap[currentImageRId]
                                    if (dataUri != null) {
                                        if (isFirstParagraph) {
                                            sbHtml.append("<div class=\"slide-content\">")
                                            isFirstParagraph = false
                                        }
                                        sbHtml.append("<img src=\"$dataUri\" alt=\"Slide Image\"/>")
                                    }
                                    currentImageRId = null
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (!isFirstParagraph) {
                sbHtml.append("</div>") // Close slide-content
            }
        } catch (_: Exception) { }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}