package com.example.simplereader.parser

import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream

class DocxParser {
    
    fun parseToHtml(inputStream: InputStream): String {
        // Apache POI XWPFDocument handles .docx files
        val document = XWPFDocument(inputStream)
        val htmlBuilder = StringBuilder()
        
        htmlBuilder.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
                <style>
                    body { 
                        font-family: sans-serif; 
                        padding: 16px; 
                        line-height: 1.6; 
                        color: #333;
                        word-wrap: break-word;
                    }
                    p { margin: 8px 0; }
                    table { 
                        border-collapse: collapse; 
                        width: 100%; 
                        margin: 16px 0;
                        overflow-x: auto;
                        display: block;
                    }
                    td, th { 
                        border: 1px solid #ddd; 
                        padding: 8px; 
                        min-width: 50px;
                    }
                    tr:nth-child(even) { background-color: #f9f9f9; }
                    img { max-width: 100%; height: auto; }
                    h1, h2, h3, h4, h5, h6 { color: #000; margin-top: 24px; }
                </style>
            </head>
            <body>
        """.trimIndent())
        
        // Parse paragraphs
        for (paragraph in document.paragraphs) {
            val text = paragraph.text
            if (text.isNotBlank()) {
                val style = paragraph.styleID
                // Simple heuristic for headers based on style ID or basic formatting could be added here
                // For now, just render as paragraphs
                htmlBuilder.append("<p>$text</p>\n")
            }
        }
        
        // Parse tables
        for (table in document.tables) {
            htmlBuilder.append("<table>")
            for (row in table.rows) {
                htmlBuilder.append("<tr>")
                for (cell in row.tableCells) {
                    htmlBuilder.append("<td>${cell.text}</td>")
                }
                htmlBuilder.append("</tr>")
            }
            htmlBuilder.append("</table>")
        }
        
        htmlBuilder.append("</body></html>")
        document.close()
        
        return htmlBuilder.toString()
    }
}
