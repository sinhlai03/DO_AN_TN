Shoe Store Management & Shopping App
📌 Giới thiệu

Đây là ứng dụng bán giày trực tuyến được phát triển nhằm hỗ trợ:
Khách hàng: tìm kiếm, mua sắm sản phẩm dễ dàng
Người quản lý (Admin): quản lý sản phẩm, đơn hàng, khách hàng
Chatbot AI: tư vấn sản phẩm, hỗ trợ lựa chọn giày phù hợp

Ứng dụng hướng tới việc nâng cao trải nghiệm người dùng và tối ưu hóa quy trình bán hàng.

🎯 Mục tiêu dự án
Xây dựng hệ thống bán hàng trực tuyến hoàn chỉnh
Áp dụng mô hình Client-Server
Tích hợp chatbot hỗ trợ mua hàng
Quản lý dữ liệu hiệu quả bằng cơ sở dữ liệu
Giao diện thân thiện, dễ sử dụng

👥 Đối tượng sử dụng
Khách hàng mua giày online
Quản trị viên cửa hàng

⚙️ Chức năng chính
👤 Khách hàng
Đăng ký / đăng nhập
Xem danh sách sản phẩm
Tìm kiếm và lọc sản phẩm (theo giá, size, loại)
Xem chi tiết sản phẩm
Thêm vào giỏ hàng
Đặt hàng
Theo dõi đơn hàng
Chat với chatbot để được tư vấn
🛠️ Quản lý (Admin)
Quản lý sản phẩm (thêm, sửa, xoá)
Quản lý danh mục
Quản lý đơn hàng
Quản lý khách hàng
Xem báo cáo thống kê (doanh thu, số đơn, sản phẩm bán chạy)

🤖 Chatbot tư vấn
Gợi ý sản phẩm theo nhu cầu người dùng
Trả lời các câu hỏi thường gặp
Hỗ trợ chọn size, loại giày
Tư vấn theo ngân sách

🏗️ Kiến trúc hệ thống
Frontend: Giao diện người dùng (Web hoặc Desktop)
Backend: Xử lý logic nghiệp vụ
Database: Lưu trữ dữ liệu
Chatbot Module: Xử lý hội thoại và gợi ý sản phẩm

🛠️ Công nghệ sử dụng
Ngôn ngữ: Java .
Frontend: Android Studio
Backend: Spring Boot / NodeJS
Database: Firebase
Chatbot: AI rule-based hoặc NLP


Các bảng chính:

Users
Products
Categories
Orders
OrderDetails
Cart
ChatHistory

🚀 Cài đặt và chạy dự án

1. Clone project
git clone <repo_url>

2. Cấu hình database
Tạo database
Import file .sql
Cập nhật thông tin kết nối trong config

4. Chạy backend
mvn spring-boot:run

6. Chạy frontend
Mở bằng trình duyệt hoặc IDE

📷 Demo (nếu có)
Trang chủ
Trang sản phẩm
Giỏ hàng
Trang admin
Chatbot

📊 Kiểm thử
Kiểm thử chức năng (Functional Testing)
Kiểm thử giao diện (UI Testing)
Kiểm thử hiệu năng (Load Testing - JMeter)
Kiểm thử chatbot (response accuracy)

🔐 Bảo mật
Xác thực người dùng (Authentication)
Phân quyền (Authorization)
Mã hoá mật khẩu

📈 Hướng phát triển
Tích hợp thanh toán online  QR (VNPay, MoMo)
Nâng cấp chatbot AI thông minh hơn
Mobile App (Android/iOS)
Gợi ý sản phẩm bằng Machine Learning
