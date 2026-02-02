package com.zeq.simple.reader.model

/**
 * Enum representing supported document types.
 * Used for routing to appropriate viewer and parser.
 */
enum class DocumentType(val mimeTypes: List<String>, val extensions: List<String>) {
    PDF(
        mimeTypes = listOf("application/pdf"),
        extensions = listOf("pdf")
    ),
    DOCX(
        mimeTypes = listOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword"
        ),
        extensions = listOf("docx", "doc")
    ),
    XLSX(
        mimeTypes = listOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel"
        ),
        extensions = listOf("xlsx", "xls")
    ),
    UNKNOWN(
        mimeTypes = emptyList(),
        extensions = emptyList()
    );

    companion object {
        /**
         * Determines document type from MIME type string.
         * Falls back to UNKNOWN if not recognized.
         */
        fun fromMimeType(mimeType: String?): DocumentType {
            if (mimeType == null) return UNKNOWN
            return entries.find { docType ->
                docType.mimeTypes.any { it.equals(mimeType, ignoreCase = true) }
            } ?: UNKNOWN
        }

        /**
         * Determines document type from file extension.
         * Extracts extension from full filename or path.
         */
        fun fromFileName(fileName: String?): DocumentType {
            if (fileName == null) return UNKNOWN
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return entries.find { docType ->
                docType.extensions.contains(extension)
            } ?: UNKNOWN
        }

        /**
         * Combined detection: tries MIME type first, then falls back to extension.
         */
        fun detect(mimeType: String?, fileName: String?): DocumentType {
            val fromMime = fromMimeType(mimeType)
            return if (fromMime != UNKNOWN) fromMime else fromFileName(fileName)
        }
    }
}
