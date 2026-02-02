# Simple Reader - Trình Đọc Tài Liệu Ngoại Tuyến Siêu Nhẹ

![poster.png](poster.png)

[Read this in English](README.md)

Một trình đọc tài liệu tập trung nghiêm ngặt vào quyền riêng tư, siêu nhẹ dành cho Android 13+.

## Tại Sao Nên Chọn Simple Reader?

Trong một thế giới đầy rẫy các ứng dụng cồng kềnh chứa đầy quảng cáo và theo dõi người dùng, Simple Reader mang đến một sự thay thế trong sạch. Ứng dụng được thiết kế cho những người dùng coi trọng quyền riêng tư, tốc độ và dung lượng lưu trữ của thiết bị.

### 1. Dung Lượng Ứng Dụng Tối Thiểu
Không giống như các trình xem khác thường đóng gói các công cụ hiển thị nặng nề (thường thêm 30-50MB), Simple Reader sử dụng các API gốc của Android và logic phân tích cú pháp được tối ưu hóa cao. Kết quả là một ứng dụng chiếm không gian không đáng kể trên điện thoại của bạn.

### 2. Thực Sự Riêng Tư & An Toàn
*   **Không Cần Quyền Internet**: Ứng dụng không thể kết nối internet. Tài liệu của bạn không bao giờ rời khỏi thiết bị.
*   **Không Phân Tích Dữ Liệu**: Chúng tôi không theo dõi việc sử dụng hoặc thu thập dữ liệu.
*   **Hiển Thị Trong Hộp Cát (Sandboxed)**: Tài liệu được xử lý trong môi trường an toàn. Các tệp Office được chuyển đổi thành HTML an toàn, loại bỏ các tập lệnh độc hại tiềm ẩn.

### 3. Tích Hợp "Mở Bằng" (Open With) Tự Nhiên
Ứng dụng tích hợp trực tiếp với trình quản lý tệp của hệ thống. Bạn không cần phải mở ứng dụng trước; chỉ cần nhấn vào tệp của bạn trong "Tải xuống" hoặc "Tệp" và chọn Simple Reader.

## Các Định Dạng Được Hỗ Trợ

*   **PDF (.pdf)**: Hiển thị hiệu suất cao sử dụng công cụ PDF tích hợp sẵn của Android. Hỗ trợ thu phóng bằng hai ngón tay và ảo hóa trang.
*   **Word (.docx, .doc)**: Nội dung được phân tích và định dạng để dễ đọc trên màn hình di động.
*   **Excel (.xlsx, .xls)**: Bảng tính được hiển thị với điều hướng tab cho nhiều trang tính.

## Điểm Nổi Bật Về Kỹ Thuật

Dành cho các lập trình viên quan tâm đến việc triển khai:

*   **Ngôn Ngữ**: 100% Kotlin.
*   **Giao Diện (UI)**: Jetpack Compose (Material 3).
*   **Kiến Trúc**: MVVM / Kiến Trúc Sạch (Clean Architecture).
*   **PDF**: Được triển khai thông qua `android.graphics.pdf.PdfRenderer` với việc tái sử dụng Bitmap và LruCache để tiết kiệm bộ nhớ.
*   **Office**: Sử dụng phiên bản rút gọn của Apache POI (đã qua ProGuard). Các tệp nhị phân phức tạp được phân tích thành HTML/CSS để hiển thị trong WebView bị hạn chế quyền.
*   **Lưu Trữ**: Tuân thủ hoàn toàn Bộ Nhớ Phạm Vi (Scoped Storage) của Android 13 (không cần quyền `MANAGE_EXTERNAL_STORAGE` quá rộng).

## Yêu Cầu

*   Android 13 (API Level 33) hoặc cao hơn.
