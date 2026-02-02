package com.zeq.simple.reader.parser

import android.util.Log
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parser for XLSX files using Apache POI.
 * Converts spreadsheet content to minimal HTML with tab navigation for multiple sheets.
 *
 * Design decisions:
 * - Uses BufferedInputStream for efficient I/O
 * - Limits row/column processing to prevent OOM on large files
 * - HTML-escapes all cell content for security
 * - Generates responsive table layout
 */
class XlsxParser {

    companion object {
        private const val TAG = "XlsxParser"
        private const val BUFFER_SIZE = 8192
        private const val MAX_ROWS = 1000
        private const val MAX_COLUMNS = 50

        private const val TAB_SCRIPT = """
    <script>
        function showSheet(index) {
            document.querySelectorAll('.sheet-content').forEach(function(el) {
                el.style.display = 'none';
            });
            document.querySelectorAll('.tab').forEach(function(el) {
                el.classList.remove('active');
            });
            document.getElementById('sheet' + index).style.display = 'block';
            document.querySelectorAll('.tab')[index].classList.add('active');
        }
    </script>
"""

        private const val HTML_FOOTER = """
</body>
</html>
"""
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Parses XLSX InputStream and converts to HTML string.
     *
     * @param inputStream Raw input stream from ContentResolver
     * @return HTML string representation of the spreadsheet
     * @throws DocumentParseException if parsing fails
     */
    fun parseToHtml(inputStream: InputStream): String {
        return try {
            BufferedInputStream(inputStream, BUFFER_SIZE).use { bufferedStream ->
                XSSFWorkbook(bufferedStream).use { workbook ->
                    buildHtmlFromWorkbook(workbook)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XLSX", e)
            throw DocumentParseException("Failed to parse XLSX document: ${e.message}", e)
        }
    }

    /**
     * Builds HTML from XSSFWorkbook, creating tabs for multiple sheets.
     */
    private fun buildHtmlFromWorkbook(workbook: XSSFWorkbook): String {
        val htmlBuilder = StringBuilder()
        val sheetCount = workbook.numberOfSheets

        htmlBuilder.append(generateHtmlHeader(workbook))

        // Generate tab buttons if multiple sheets
        if (sheetCount > 1) {
            htmlBuilder.append("<div class=\"tabs\">\n")
            for (i in 0 until sheetCount) {
                val sheetName = escapeHtml(workbook.getSheetName(i))
                val activeClass = if (i == 0) " active" else ""
                htmlBuilder.append("<button class=\"tab$activeClass\" onclick=\"showSheet($i)\">$sheetName</button>\n")
            }
            htmlBuilder.append("</div>\n")
        }

        // Generate content for each sheet
        for (i in 0 until sheetCount) {
            val sheet = workbook.getSheetAt(i)
            val displayStyle = if (i == 0) "block" else "none"
            htmlBuilder.append("<div id=\"sheet$i\" class=\"sheet-content\" style=\"display: $displayStyle;\">\n")

            if (sheetCount == 1) {
                val sheetName = escapeHtml(workbook.getSheetName(i))
                htmlBuilder.append("<h2 class=\"sheet-title\">$sheetName</h2>\n")
            }

            htmlBuilder.append("<div class=\"table-wrapper\">\n<table>\n")

            var rowCount = 0
            val rowIterator = sheet.rowIterator()

            while (rowIterator.hasNext() && rowCount < MAX_ROWS) {
                val row = rowIterator.next()
                htmlBuilder.append("<tr>\n")

                val lastCellNum = minOf(row.lastCellNum.toInt(), MAX_COLUMNS)
                for (cellIndex in 0 until lastCellNum) {
                    val cell = row.getCell(cellIndex)
                    val cellValue = getCellValueAsString(cell)
                    val tag = if (rowCount == 0) "th" else "td"
                    htmlBuilder.append("<$tag>${escapeHtml(cellValue)}</$tag>\n")
                }

                htmlBuilder.append("</tr>\n")
                rowCount++
            }

            htmlBuilder.append("</table>\n</div>\n")

            // Show truncation warning if needed
            if (rowCount >= MAX_ROWS) {
                htmlBuilder.append("<p class=\"warning\">⚠️ Showing first $MAX_ROWS rows only</p>\n")
            }

            htmlBuilder.append("</div>\n")
        }

        htmlBuilder.append(HTML_FOOTER)
        return htmlBuilder.toString()
    }

    /**
     * Extracts cell value as string, handling different cell types.
     */
    private fun getCellValueAsString(cell: Cell?): String {
        if (cell == null) return ""

        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue ?: ""
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.dateCellValue?.let { dateFormat.format(it) } ?: ""
                    } else {
                        val numValue = cell.numericCellValue
                        if (numValue == numValue.toLong().toDouble()) {
                            numValue.toLong().toString()
                        } else {
                            String.format(Locale.US, "%.2f", numValue)
                        }
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        cell.stringCellValue ?: cell.numericCellValue.toString()
                    } catch (e: Exception) {
                        cell.cellFormula ?: ""
                    }
                }
                CellType.BLANK -> ""
                CellType.ERROR -> "#ERROR"
                else -> ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading cell value", e)
            ""
        }
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

    /**
     * Generates HTML header with CSS and JavaScript for tab navigation.
     */
    private fun generateHtmlHeader(workbook: XSSFWorkbook): String {
        val hasMultipleSheets = workbook.numberOfSheets > 1
        val tabScript = if (hasMultipleSheets) TAB_SCRIPT else ""

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        * {
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            font-size: 14px;
            line-height: 1.4;
            color: #212121;
            padding: 8px;
            margin: 0;
            background-color: #ffffff;
        }
        @media (prefers-color-scheme: dark) {
            body {
                background-color: #121212;
                color: #e0e0e0;
            }
            .tabs {
                background-color: #1e1e1e;
                border-color: #424242;
            }
            .tab {
                background-color: #2d2d2d;
                color: #e0e0e0;
                border-color: #424242;
            }
            .tab:hover {
                background-color: #3d3d3d;
            }
            .tab.active {
                background-color: #1976d2;
                color: #ffffff;
            }
            table {
                border-color: #424242;
            }
            th {
                background-color: #1e1e1e;
                border-color: #424242;
            }
            td {
                border-color: #424242;
            }
            tr:hover {
                background-color: #2d2d2d;
            }
        }
        .tabs {
            display: flex;
            flex-wrap: wrap;
            gap: 4px;
            padding: 8px;
            background-color: #f5f5f5;
            border-radius: 8px;
            margin-bottom: 16px;
        }
        .tab {
            padding: 8px 16px;
            border: 1px solid #e0e0e0;
            border-radius: 4px;
            background-color: #ffffff;
            cursor: pointer;
            font-size: 13px;
            font-weight: 500;
            transition: all 0.2s ease;
        }
        .tab:hover {
            background-color: #e3f2fd;
        }
        .tab.active {
            background-color: #1976d2;
            color: #ffffff;
            border-color: #1976d2;
        }
        .sheet-title {
            font-size: 18px;
            margin: 0 0 12px 0;
            font-weight: 600;
        }
        .table-wrapper {
            overflow-x: auto;
            -webkit-overflow-scrolling: touch;
        }
        table {
            border-collapse: collapse;
            font-size: 13px;
            white-space: nowrap;
            min-width: 100%;
        }
        th, td {
            border: 1px solid #e0e0e0;
            padding: 6px 10px;
            text-align: left;
            max-width: 300px;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        th {
            background-color: #f5f5f5;
            font-weight: 600;
            position: sticky;
            top: 0;
            z-index: 1;
        }
        tr:hover {
            background-color: #f5f5f5;
        }
        .warning {
            color: #f57c00;
            font-size: 13px;
            margin-top: 12px;
            padding: 8px;
            background-color: #fff3e0;
            border-radius: 4px;
        }
        @media (prefers-color-scheme: dark) {
            .warning {
                background-color: #3e2723;
            }
        }
    </style>
    $tabScript
</head>
<body>
"""
    }
}
