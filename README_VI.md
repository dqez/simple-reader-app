# Simple Document Reader

Ứng dụng Android hỗ trợ đọc tài liệu offline (.pdf, .docx, .xlsx).

## Tính năng

- Đọc file PDF (Native rendering).
- Đọc file DOCX và XLSX (HTML conversion rendering).
- Hỗ trợ Zoom.
- Hoạt động Offline hoàn toàn.
- Tích hợp File Association (mở file từ ứng dụng quản lý file).

## Công nghệ

- Ngôn ngữ: Kotlin
- PDF: libraries/Pdf-Viewer
- Office: Apache POI (xử lý parse file DOCX/XLSX)
- View: WebView (hiển thị nội dung DOCX/XLSX sau khi convert)

## Cài đặt và Build

Yêu cầu: Android SDK 26+

Build APK:

```bash
./gradlew assembleRelease
```

## Lưu ý kỹ thuật

- APK Size: ~20-30MB (do tích hợp thư viện Apache POI để xử lý offline).
- Xử lý file: File DOCX/XLSX được convert sang HTML trong bộ nhớ và hiển thị lên WebView. Hiệu năng phụ thuộc vào RAM thiết bị đối với các file kích thước lớn.

## Cấu trúc Project

- `parser/`: Chứa logic convert Docx/Xlsx sang HTML.
- `ui/`: Chứa Activity hiển thị tài liệu.
- `utils/`: Tiện ích xử lý file.
