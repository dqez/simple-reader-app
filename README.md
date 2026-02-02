# Simple Reader - Lightweight Offline Document Viewer

![poster.png](poster.png)

[Đọc tài liệu này bằng Tiếng Việt](README_VI.md)

A strictly privacy-focused, ultra-lightweight document reader for Android 13+. 

## Why Simple Reader?

In a world of bloated apps filled with ads and tracking, Simple Reader offers a clean alternative. It is designed for users who value privacy, speed, and device storage.

### 1. Minimal App Size
Unlike other viewers that bundle heavy rendering engines (often adding 30-50MB), Simple Reader uses Android's native APIs and a highly optimized parsing logic. The result is an app that takes up negligible space on your phone.

### 2. Truly Private & Secure
*   **No Internet Permission**: The app cannot connect to the internet. Your documents never leave your device.
*   **No Analytics**: We do not track usage or collect data.
*   **Sandboxed Rendering**: Documents are processed in a secure environment. Office files are converted to safe HTML, stripping potential malicious scripts.

### 3. Native "Open With" Integration
The app integrates directly with your system's file manager. You don't need to open the app first; just tap your file in "Downloads" or "Files" and select Simple Reader.

## Supported Formats

*   **PDF (.pdf)**: High-performance rendering using Android's built-in PDF engine. Supports pinch-to-zoom and page virtualization.
*   **Word (.docx, .doc)**: content is parsed and formatted for readability on mobile screens.
*   **Excel (.xlsx, .xls)**: Spreadsheets are rendered with tab navigation for multiple sheets.

## Technical Highlights

For developers interested in the implementation:

*   **Language**: 100% Kotlin.
*   **UI**: Jetpack Compose (Material 3).
*   **Architecture**: MVVM / Clean Architecture.
*   **PDF**: Implemented via `android.graphics.pdf.PdfRenderer` with Bitmap recycling and LruCache for memory efficiency.
*   **Office**: Uses a ProGuard-stripped version of Apache POI. Complex binaries are parsed to HTML/CSS for rendering in a restricted WebView.
*   **Storage**: Fully compliant with Android 13 Scoped Storage (no overly broad `MANAGE_EXTERNAL_STORAGE` permission needed).

## Requirements

*   Android 13 (API Level 33) or higher.
