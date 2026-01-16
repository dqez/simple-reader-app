# Simple Document Reader

[Tiếng Việt](README_VI.md)

Android application for offline document reading (.pdf, .docx, .xlsx).

## Features

- Read PDF files (Native rendering).
- Read DOCX and XLSX files (HTML conversion rendering).
- Zoom support.
- Fully Offline.
- File Association support (open files from system file managers).

## Technology

- Language: Kotlin
- PDF: libraries/Pdf-Viewer
- Office: Apache POI (handles parsing of DOCX/XLSX)
- View: WebView (displays DOCX/XLSX content after conversion)

## Installation and Build

Requirements: Android SDK 26+

Build APK:

```bash
./gradlew assembleRelease
```

## Technical Notes

- APK Size: ~20-30MB (due to Apache POI integration for offline processing).
- File Processing: DOCX/XLSX files are converted to HTML in memory and rendered via WebView. Performance for large files depends on device RAM.

## Project Structure

- `parser/`: Logic for converting Docx/Xlsx to HTML.
- `ui/`: Activities for document viewing.
- `utils/`: File utility helper classes.
