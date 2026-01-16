package com.example.simplereader.parser

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

class XlsxParser {
    
    fun parseToHtml(inputStream: InputStream): String {
        val workbook = XSSFWorkbook(inputStream)
        val htmlBuilder = StringBuilder()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        
        htmlBuilder.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
                <style>
                    body { font-family: sans-serif; padding: 8px; }
                    .sheet { margin-bottom: 32px; border: 1px solid #ccc; }
                    .sheet-name { 
                        font-weight: bold; 
                        background: #4CAF50; 
                        color: white;
                        padding: 12px; 
                        font-size: 1.1em;
                    }
                    .table-container {
                        overflow-x: auto;
                    }
                    table { 
                        border-collapse: collapse; 
                        width: 100%; 
                        margin-top: 0; 
                    }
                    td, th { 
                        border: 1px solid #ddd; 
                        padding: 8px; 
                        text-align: left; 
                        white-space: nowrap;
                    }
                    tr:nth-child(even) { background-color: #f2f2f2; }
                    th { background-color: #e0e0e0; }
                </style>
            </head>
            <body>
        """.trimIndent())
        
        for (sheetIndex in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetIndex)
            htmlBuilder.append("<div class='sheet'>")
            htmlBuilder.append("<div class='sheet-name'>${sheet.sheetName}</div>")
            htmlBuilder.append("<div class='table-container'><table>")
            
            for (row in sheet) {
                htmlBuilder.append("<tr>")
                for (cell in row) {
                    val cellValue = try {
                        when (cell.cellType) {
                            CellType.STRING -> cell.stringCellValue
                            CellType.NUMERIC -> {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    dateFormat.format(cell.dateCellValue)
                                } else {
                                    // Remove trailing zeros for integers
                                    val value = cell.numericCellValue
                                    if (value == value.toLong().toDouble()) {
                                        value.toLong().toString()
                                    } else {
                                        value.toString()
                                    }
                                }
                            }
                            CellType.BOOLEAN -> cell.booleanCellValue.toString()
                            CellType.FORMULA -> {
                                try {
                                    cell.stringCellValue
                                } catch (e: Exception) {
                                    try {
                                        cell.numericCellValue.toString()
                                    } catch (e2: Exception) {
                                        cell.cellFormula
                                    }
                                }
                            }
                            else -> ""
                        }
                    } catch (e: Exception) {
                        ""
                    }
                    htmlBuilder.append("<td>$cellValue</td>")
                }
                htmlBuilder.append("</tr>")
            }
            
            htmlBuilder.append("</table></div></div>")
        }
        
        htmlBuilder.append("</body></html>")
        workbook.close()
        
        return htmlBuilder.toString()
    }
}
