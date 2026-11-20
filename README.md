# UTEExpress — Hệ thống Quản lý Chuỗi Giao nhận Logistic

Website quản lý chuỗi giao nhận vận chuyển đa bưu cục (Multi-Hub Logistics Chain) xây dựng bằng **Spring Boot 3 (Java 21)**, **Thymeleaf**, **Bootstrap 5**, **Spring Data JPA**, **PostgreSQL 16 (Docker)**, **JWT**, **WebSocket (STOMP/SockJS)**, **VNPay Sandbox**, **OpenPDF + ZXing** và **Cloudflare R2**.

---

## Tính năng Chính

* **Cổng Công khai:** Tra cứu 1–5 vận đơn cùng lúc kèm **Live Tracking WebSocket** (che thông tin nhạy cảm), công cụ ước tính cước công khai và tra cứu mạng lưới bưu cục.
* **Cổng Khách hàng (`CUSTOMER`):** Tạo vận đơn tự động tính cước (hỗ trợ trọng lượng quy đổi `D×R×C / 5000`, đồng kiểm, hàng dễ vỡ), thanh toán cước trực tuyến qua **VNPay Sandbox**, xuất **tem vận đơn PDF A6/A5** (Barcode Code128 + QR Code + Routing Code), sổ địa chỉ, hồ sơ ngân hàng & lịch sử đối soát COD.
* **Cổng Bưu cục (`STAFF`):** Điều phối chuỗi đa bưu cục (gán shipper lấy/giao, nhập kho gửi, xuất trung chuyển liên tỉnh, nhập kho nhận, xử lý giao lại tối đa 3 lần / hoàn hàng), thu tiền COD từ shipper và chat CSKH realtime.
* **Cổng Giao nhận (`SHIPPER`):** Quản lý tác vụ lấy/giao hàng, bắt buộc upload **ảnh bằng chứng giao hàng (POD)** lên **Cloudflare R2** khi giao thành công, theo dõi tiền COD đang giữ.
* **Cổng Quản trị (`ADMIN`):** Quản lý chuỗi bưu cục/kho trung chuyển (`Hub`), bảng giá gói dịch vụ (`ServiceType`), tài khoản nhân sự, duyệt **phiếu đối soát COD có cấn trừ cước** và Dashboard thống kê biểu đồ **Chart.js**.

---

## Tech Stack

| Thành phần | Công nghệ |
| :--- | :--- |
| Backend | Java 21 LTS, Spring Boot 3.4.x, Maven 3.9+ |
| View & UI | Thymeleaf, Thymeleaf Layout Dialect, Bootstrap 5.3, Chart.js |
| Database & Migration | PostgreSQL 16 (Docker Compose), Spring Data JPA, Flyway |
| Bảo mật | Spring Security 6, JWT (`HS256`, HttpOnly Cookie & Bearer Header) |
| Realtime | Spring WebSocket (STOMP + SockJS) |
| Tích hợp | VNPay Sandbox (HMAC-SHA512), OpenPDF + ZXing (PDF A6/A5), Cloudflare R2 (AWS SDK v2 S3) |

---

## Thành viên Thực hiện

| Thành viên | Phân hệ phụ trách chính |
| :--- | :--- |
| **Nguyễn Nguyên** *(Nhóm trưởng)* | Khung dự án, Docker/Flyway, Security JWT, Admin CRUD & Dashboard Chart.js |
| **Lê Đàm Nguyên** | Pricing Engine, Customer Portal, VNPay Sandbox & Xuất Tem PDF A6/A5 |
| **Mạnh Tiến** | Shipment State Machine, Staff/Shipper Portal & Cloudflare R2 (POD) |
| **Thế Quân** | Public Portal, WebSocket (Live Tracking, Notify, Chat CSKH) & Đối soát COD |

---

## Tài liệu Dự án (`docs/`)

1. [01-BRD.md](docs/01-BRD.md) — Yêu cầu Nghiệp vụ & Quy tắc nghiệp vụ cốt lõi
2. [02-PRD.md](docs/02-PRD.md) — Yêu cầu Sản phẩm, Personas & User Stories
3. [03-SRS.md](docs/03-SRS.md) — Đặc tả Phần mềm, State Machine & Sequence Diagrams
4. [04-ARCHITECTURE.md](docs/04-ARCHITECTURE.md) — Kiến trúc Hệ thống & Cấu trúc Source Code
5. [05-DATABASE-DESIGN.md](docs/05-DATABASE-DESIGN.md) — Sơ đồ ERD, 9 bảng PostgreSQL & Flyway Seed Data
6. [06-API-AND-ROUTES.md](docs/06-API-AND-ROUTES.md) — Web MVC Routes, REST API & WebSocket Contract
7. [07-UI-UX-SPEC.md](docs/07-UI-UX-SPEC.md) — Design System Bootstrap 5 & Bố cục các màn hình
8. [08-TEST-PLAN.md](docs/08-TEST-PLAN.md) — Ma trận Phân quyền & Kịch bản Kiểm thử
9. [09-ROADMAP.md](docs/09-ROADMAP.md) — Kế hoạch Triển khai theo Phase & Phân công 4 thành viên

---

## Hướng dẫn Cài đặt & Chạy Local

### 1. Yêu cầu môi trường
* **Java 21+** (`java -version`)
* **Maven 3.9+** (`mvn -version`)
* **Docker & Docker Compose** (`docker compose version`)

### 2. Cấu hình biến môi trường
Sao chép file mẫu và điền thông số thực tế (PostgreSQL, JWT Secret, VNPay Sandbox, Cloudflare R2):

```bash
cp .env.example .env
```

### 3. Khởi động cơ sở dữ liệu PostgreSQL 16
```bash
docker compose up -d
```

Kiểm tra container database đã sẵn sàng (`healthy`):
```bash
docker compose ps
```

### 4. Chạy ứng dụng Spring Boot *(sau khi khởi tạo source code ở Phase 0)*
```bash
mvn spring-boot:run
```

Truy cập trình duyệt tại: `http://localhost:8080`

### 5. Tài khoản Demo mặc định *(seed bởi Flyway `V2__seed_data.sql` — mật khẩu: `123456`)*
* **Admin:** `admin@uteexpress.vn`
* **Staff TP.HCM (`HCM-TD01`):** `staff.hcm@uteexpress.vn`
* **Staff Hà Nội (`HN-CG01`):** `staff.hn@uteexpress.vn`
* **Shipper TP.HCM (`HCM-TD01`):** `shipper.hcm@uteexpress.vn`
* **Shipper Hà Nội (`HN-CG01`):** `shipper.hn@uteexpress.vn`
* **Customer (Shop):** `shop@uteexpress.vn`
