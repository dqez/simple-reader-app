package com.zeq.simple.reader.parser

import android.content.ContentResolver
import android.net.Uri
import org.xmlpull.v1.XmlPullParser

class XlsxParser {

    /**
     * Parses XLSX Uri and converts to HTML string using streaming XML parsing.
     * Uses multiple passes: sharedStrings -> workbook (names) -> sheets.
     * Zero-dependency implementation removing Apache POI.
     */
    fun parse(contentResolver: ContentResolver, uri: Uri): ParseResult {
        val sharedStrings = ArrayList<String>()
        val sheetNames = ArrayList<String>()
        val sbHtml = StringBuilder()
        val textList = mutableListOf<String>()
        val sbTabs = StringBuilder()
        val sbSheets = StringBuilder()

        val headerColor = "#f5f5f5"
        val activeTabColor = "#2196F3"
        val highlightColor = "#fff9c4" // Yellow highlight

        // Script for Tab Switching & Search
        val script = """
            <script>
                // Tab Logic
                function openSheet(sheetId) {
                    var i;
                    var x = document.getElementsByClassName("sheet");
                    for (i = 0; i < x.length; i++) {
                        x[i].style.display = "none";
                    }
                    var tablinks = document.getElementsByClassName("tab-btn");
                    for (i = 0; i < tablinks.length; i++) {
                        tablinks[i].className = tablinks[i].className.replace(" active", "");
                    }
                    
                    // Activate tab button
                    var activeBtn = document.getElementById("btn_" + sheetId);
                    if (activeBtn) activeBtn.className += " active";
                    
                    // Show sheet
                    document.getElementById(sheetId).style.display = "block";
                }

                // Search Logic
                var currentMatchIndex = -1;
                var matches = [];

                function performSearch() {
                    var input = document.getElementById("searchInput");
                    var filter = input.value.toUpperCase();
                    var cells = document.getElementsByTagName("td");
                    
                    // Reset previous highlights
                    for (var i = 0; i < matches.length; i++) {
                        matches[i].classList.remove("highlight");
                    }
                    matches = [];
                    currentMatchIndex = -1;

                    if (filter === "") return;

                    for (var i = 0; i < cells.length; i++) {
                        var txtValue = cells[i].textContent || cells[i].innerText;
                        if (txtValue.toUpperCase().indexOf(filter) > -1) {
                            matches.push(cells[i]);
                            cells[i].classList.add("highlight");
                        }
                    }
                    
                    if (matches.length > 0) {
                        scrollToMatch(0);
                        document.getElementById("searchStatus").innerText = "1 / " + matches.length;
                    } else {
                        document.getElementById("searchStatus").innerText = "0 / 0";
                    }
                }
                
                function nextMatch() {
                   if (matches.length == 0) return;
                   currentMatchIndex = (currentMatchIndex + 1) % matches.length;
                   scrollToMatch(currentMatchIndex);
                   document.getElementById("searchStatus").innerText = (currentMatchIndex + 1) + " / " + matches.length;
                }

                function scrollToMatch(index) {
                    var target = matches[index];
                    
                    // 1. Find which sheet this cell belongs to
                    var parent = target.parentElement; // tr
                    while (parent && !parent.classList.contains("sheet")) {
                        parent = parent.parentElement;
                    }
                    
                    // 2. Switch to that sheet
                    if (parent) {
                        openSheet(parent.id);
                    }
                    
                    // 3. Scroll view
                    target.scrollIntoView({behavior: "smooth", block: "center", inline: "center"});
                }
            </script>
        """.trimIndent()

        val style = """
            <style>
                body { padding: 0; font-family: sans-serif; margin: 0; display: flex; flex-direction: column; height: 100vh; }
                
                /* Top Control Bar */
                .controls { background: #fff; border-bottom: 2px solid #ddd; padding: 5px; flex-shrink: 0; display: flex; align-items: center; gap: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); z-index: 100; }
                .search-box { flex-grow: 1; padding: 8px; border: 1px solid #ccc; border-radius: 4px; }
                .nav-btn { padding: 8px 12px; background: #eee; border: 1px solid #ccc; border-radius: 4px; cursor: pointer; }
                
                /* Tabs */
                .tab-bar { overflow-x: auto; background-color: #f1f1f1; border-bottom: 1px solid #ccc; white-space: nowrap; -webkit-overflow-scrolling: touch; flex-shrink: 0; }
                .tab-btn { background-color: inherit; display: inline-block; border: none; outline: none; cursor: pointer; padding: 10px 16px; transition: 0.3s; font-size: 14px; font-weight: 500; border-right: 1px solid #ddd; }
                .tab-btn:hover { background-color: #ddd; }
                .tab-btn.active { background-color: $activeTabColor; color: white; }
                
                /* Content */
                .content-area { flex-grow: 1; overflow: auto; position: relative; }
                .sheet { display: none; padding: 0; animation: fadeEffect 0.3s; }
                @keyframes fadeEffect { from {opacity: 0;} to {opacity: 1;} }
                
                /* Table */
                table { border-collapse: separate; border-spacing: 0; width: 100%; font-size: 13px; table-layout: fixed; }
                td, th { border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0; padding: 6px; min-width: 60px; white-space: pre-wrap; word-wrap: break-word; vertical-align: top; }
                
                /* Row Headers (Row Numbers) */
                .row-num { background-color: $headerColor; font-weight: bold; text-align: center; color: #666; width: 40px; min-width: 40px; border-right: 2px solid #ccc; position: sticky; left: 0; z-index: 10; }
                
                tr:nth-child(even) td { background-color: #fafafa; }
                tr:hover td { background-color: #f0f0f0; }
                
                /* Highlighting */
                .highlight { background-color: $highlightColor !important; outline: 2px solid orange; box-shadow: 0 0 5px rgba(255,165,0,0.5); }
            </style>
        """.trimIndent()

        sbHtml.append("<!DOCTYPE html><html><head>")
        sbHtml.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sbHtml.append(style)
        sbHtml.append(script)
        sbHtml.append("</head><body>")

        // 1. Controls (Search)
        sbHtml.append("""
            <div class="controls">
                <input type="text" id="searchInput" class="search-box" placeholder="Search..." onkeyup="if(event.key === 'Enter') performSearch()">
                <button class="nav-btn" onclick="performSearch()">🔍</button>
                <button class="nav-btn" onclick="nextMatch()">⬇</button>
                <span id="searchStatus" style="font-size: 12px; color: #666; min-width: 40px;"></span>
            </div>
        """.trimIndent())

        // Pass 1: Read Shared Strings
        try {
            CoreOfficeParser.parseZipFile(contentResolver, uri, setOf("xl/sharedStrings.xml")) { _, stream ->
                val parser = CoreOfficeParser.createXmlParser(stream)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "t") {
                        sharedStrings.add(parser.nextText())
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) { }

        // Pass 1.5: Read Workbook (Sheet Names)
        try {
            CoreOfficeParser.parseZipFile(contentResolver, uri, setOf("xl/workbook.xml")) { _, stream ->
                val parser = CoreOfficeParser.createXmlParser(stream)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                        val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                        sheetNames.add(name)
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) { }

        // 2. Tabs Container
        sbTabs.append("<div class='tab-bar'>")

        // Pass 2: Read Sheets
        var sheetIndex = 0
        CoreOfficeParser.parseZipFile(contentResolver, uri, setOf("xl/worksheets/sheet.*\\.xml")) { name, stream ->
             val currentIdx = sheetIndex
             val displayName = if (currentIdx < sheetNames.size) sheetNames[currentIdx] else "Sheet ${currentIdx + 1}"
             val sheetId = "sheet$currentIdx"
             val activeClass = if (currentIdx == 0) " active" else ""
             val displayStyle = if (currentIdx == 0) "block" else "none"

             // Tab Button
             sbTabs.append("<button id='btn_$sheetId' class='tab-btn$activeClass' onclick=\"openSheet('$sheetId')\">$displayName</button>")

             // Sheet Content
             sbSheets.append("<div id='$sheetId' class='sheet' style='display:$displayStyle'>")
             sbSheets.append("<table>")

             val parser = CoreOfficeParser.createXmlParser(stream)
             var eventType = parser.eventType

             // Row tracking for numbering
             var currentRowNum = 0

             while (eventType != XmlPullParser.END_DOCUMENT) {
                 if (eventType == XmlPullParser.START_TAG && parser.name == "row") {
                     currentRowNum++
                     val rAttr = parser.getAttributeValue(null, "r")
                     val rowNumDisplay = rAttr ?: currentRowNum.toString()

                     sbSheets.append("<tr>")
                     // Add Row Number Cell
                     sbSheets.append("<td class='row-num'>$rowNumDisplay</td>")

                 } else if (eventType == XmlPullParser.START_TAG && parser.name == "c") {
                     val type = parser.getAttributeValue(null, "t")
                     var cellValue = ""
                     var inCell = true
                     while (inCell) {
                          val nextEvent = parser.next()
                          try {
                              if (nextEvent == XmlPullParser.START_TAG && parser.name == "v") {
                                  val rawVal = parser.nextText()
                                  cellValue = if (type == "s") {
                                      val idx = rawVal.toIntOrNull() ?: -1
                                      if (idx >= 0 && idx < sharedStrings.size) sharedStrings[idx] else rawVal
                                  } else {
                                      rawVal
                                  }
                              } else if (nextEvent == XmlPullParser.END_TAG && parser.name == "c") {
                                  inCell = false
                              } else if (nextEvent == XmlPullParser.END_DOCUMENT) {
                                  inCell = false
                              }
                          } catch(e: Exception) { inCell = false }
                     }
                     sbSheets.append("<td>").append(escapeHtml(cellValue)).append("</td>")
                     if (cellValue.isNotEmpty()) textList.add(cellValue)
                 } else if (eventType == XmlPullParser.END_TAG && parser.name == "row") {
                     sbSheets.append("</tr>")
                 }
                 eventType = parser.next()
             }
             sbSheets.append("</table></div>")
             sheetIndex++
        }

        sbTabs.append("</div>")

        // Assemble Final HTML structure
        sbHtml.append(sbTabs)
        sbHtml.append("<div class='content-area'>")
        sbHtml.append(sbSheets)
        sbHtml.append("</div>") // End content-area
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
