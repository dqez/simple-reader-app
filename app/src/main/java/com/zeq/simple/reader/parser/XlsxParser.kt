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
        var sbHtml = StringBuilder()
        val textList = mutableListOf<String>()
        val sbTabs = StringBuilder()
        val sbSheets = StringBuilder()

        val headerColor = "#f5f5f5"
        val activeTabColor = "#2196F3"

        // Script for Tab Switching
        val script = """
            <script>
                function openSheet(sheetId) {
                    var i;
                    var x = document.getElementsByClassName("sheet");
                    for (i = 0; i < x.length; i++) {
                        x[i].style.display = "none";
                    }
                    var tablinks = document.getElementsByClassName("tab-btn");
                    for (i = 0; i < x.length; i++) {
                        tablinks[i].className = tablinks[i].className.replace(" active", "");
                    }
                    document.getElementById(sheetId).style.display = "block";
                    event.currentTarget.className += " active";
                }
            </script>
        """.trimIndent()

        val style = """
            <style>
                body { padding: 0; font-family: sans-serif; margin: 0; }
                .tab-bar { overflow: hidden; background-color: #f1f1f1; border-bottom: 1px solid #ccc; white-space: nowrap; overflow-x: auto; -webkit-overflow-scrolling: touch; }
                .tab-btn { background-color: inherit; float: left; border: none; outline: none; cursor: pointer; padding: 14px 16px; transition: 0.3s; font-size: 14px; font-weight: 500; }
                .tab-btn:hover { background-color: #ddd; }
                .tab-btn.active { background-color: $activeTabColor; color: white; }
                .sheet { display: none; padding: 0; animation: fadeEffect 0.5s; }
                @keyframes fadeEffect { from {opacity: 0;} to {opacity: 1;} }
                
                table { border-collapse: collapse; width: 100%; font-size: 13px; }
                td, th { border: 1px solid #e0e0e0; padding: 8px; min-width: 60px; max-width: 300px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                th { background-color: $headerColor; font-weight: bold; text-align: center; }
                tr:nth-child(even) { background-color: #f9f9f9; }
                tr:hover { background-color: #e8e8e8; }
            </style>
        """.trimIndent()

        sbHtml.append("<!DOCTYPE html><html><head>")
        sbHtml.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sbHtml.append(style)
        sbHtml.append(script)
        sbHtml.append("</head><body>")

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

        // Pass 2: Read Sheets
        var sheetIndex = 0
        sbTabs.append("<div class='tab-bar'>")

        CoreOfficeParser.parseZipFile(contentResolver, uri, setOf("xl/worksheets/sheet.*\\.xml")) { name, stream ->
             val currentIdx = sheetIndex
             val displayName = if (currentIdx < sheetNames.size) sheetNames[currentIdx] else "Sheet ${currentIdx + 1}"
             val sheetId = "sheet$currentIdx"
             val activeClass = if (currentIdx == 0) " active" else ""
             val displayStyle = if (currentIdx == 0) "block" else "none"

             // Tab Button
             sbTabs.append("<button class='tab-btn$activeClass' onclick=\"openSheet('$sheetId')\">$displayName</button>")

             // Sheet Content
             sbSheets.append("<div id='$sheetId' class='sheet' style='display:$displayStyle'>")
             sbSheets.append("<table>")

             val parser = CoreOfficeParser.createXmlParser(stream)
             var eventType = parser.eventType

             while (eventType != XmlPullParser.END_DOCUMENT) {
                 if (eventType == XmlPullParser.START_TAG && parser.name == "row") {
                     sbSheets.append("<tr>")
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
                              } else if (nextEvent == XmlPullParser.END_DOCUMENT) { // Safety break
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

        // Assemble HTML
        sbHtml.append(sbTabs)
        sbHtml.append(sbSheets)
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
