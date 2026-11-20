# Project Context & Agent Guidelines — UTEExpress

## 1. Tech Stack & Architecture
- **Backend:** Java 21 LTS, Spring Boot 3.4.x, Maven 3.9+, Standard Layered Monolith (`controller` → `service` → `repository` → `entity`), Spring Data JPA, Flyway, PostgreSQL 16.
- **Frontend (SSR):** Thymeleaf + `thymeleaf-layout-dialect`, Bootstrap 5.3, Bootstrap Icons, Chart.js, Vanilla JS + SockJS/STOMP (không dùng Node/Webpack/SPA framework).
- **Security & Realtime:** Spring Security 6 + JWT (`HS256`, `STATELESS`, lưu trong `HttpOnly Cookie` `ACCESS_TOKEN` và hỗ trợ header `Authorization: Bearer`); Spring WebSocket STOMP với Simple In-Memory Broker.
- **Integrations:** VNPay Sandbox API v2.1.0 (`HMAC-SHA512`), OpenPDF + ZXing (tem vận đơn PDF A6/A5 hỗ trợ tiếng Việt), Cloudflare R2 Object Storage (`software.amazon.awssdk:s3` — bucket private lưu ảnh POD).
- **Infra:** Docker Compose (chỉ duy nhất 1 container `postgres:16-alpine`).
- **Tài liệu đặc tả:** Xem đầy đủ trong `docs/01-BRD.md` đến `docs/09-ROADMAP.md` trước khi triển khai bất kỳ tính năng nào.

## 2. Key Commands
- Khởi động PostgreSQL 16: `docker compose up -d`
- Kiểm tra trạng thái DB: `docker compose ps`
- Biên dịch & chạy Unit Tests: `mvn test`
- Chạy ứng dụng Spring Boot local: `mvn spring-boot:run`
- Kiểm tra đóng gói: `mvn clean package -DskipTests`

## 3. Coding Conventions
- **Backend (Layered & Thực dụng):**
  - Tổ chức đúng package trong `com.uteexpress`: `config`, `security`, `enums`, `entity`, `repository`, `dto`, `service`, `controller/{web,api,ws}`, `exception`.
  - Mọi logic nghiệp vụ, kiểm tra chuyển trạng thái vận đơn (`Map<ShipmentStatus, Set<ShipmentStatus>>`) và kiểm tra quyền dữ liệu chống IDOR (`customer_id`, `hub_id`, `shipper_id`) phải đặt trong `service/`. `controller/` chỉ nhận request, validate đầu vào và gọi `service/`.
  - Nghiệp vụ ghi nhiều bảng bắt buộc bọc trong `@Transactional`.
  - Database: PK dùng `BIGSERIAL` (`Long`), Enum lưu `VARCHAR` (`@Enumerated(EnumType.STRING)`), tiền VNĐ dùng `NUMERIC(15,0)` (`BigDecimal`), thời gian dùng `TIMESTAMPTZ` (múi giờ `Asia/Ho_Chi_Minh`).
- **Frontend (Web Bootstrap 5):**
  - Mọi trang Thymeleaf phải kế thừa `layout/base-public.html` hoặc `layout/base-portal.html`.
  - Giao diện web responsive bằng Bootstrap 5 trên trình duyệt, không tách riêng giao diện kiểu mobile app.
  - Xử lý lỗi tách biệt: `/api/v1/**` trả JSON `ApiErrorResponse` qua `ApiExceptionHandler`; Web MVC xử lý qua `WebExceptionHandler` và Flash Message.
- **Naming:** `PascalCase` cho Java classes/enums; `camelCase` cho biến/hàm Java, JSON và JS; `snake_case` cho bảng/cột PostgreSQL; `kebab-case` cho tên file template/CSS/JS và URL routes.

## 4. Strict Rules (Do Not)
- **KHÔNG over-engineering:** Không tự ý thêm Redis, RabbitMQ/Kafka, Microservices, CQRS, MapStruct, thư viện State Machine ngoài, Optimistic Locking (`@Version`), bảng `refresh_tokens`, hay `pgAdmin`.
- **KHÔNG sửa trực tiếp các file Flyway migration đã commit** trong `src/main/resources/db/migration/`; mọi thay đổi schema phải tạo file mới (`V3__...sql`). Giữ `spring.jpa.hibernate.ddl-auto=validate`.
- **KHÔNG tự ý thêm dependency vào `pom.xml`** hoặc sửa các file lõi dùng chung (`SecurityConfig.java`, `base-portal.html`, `application.yml`) nếu chưa hỏi ý kiến.
- **KHÔNG hardcode mật khẩu, `JWT_SECRET`, `VNPAY_HASH_SECRET`, hay thông số `Cloudflare R2`** trong source code; bắt buộc đọc qua biến môi trường (`.env`) và không bao giờ commit file `.env`.
- **KHÔNG mở public bucket Cloudflare R2** cho ảnh POD (phục vụ có kiểm tra quyền qua `GET /files/pod/{shipmentId}`) và **KHÔNG để lộ SĐT đầy đủ, COD hoặc ảnh POD** trên trang `/tracking` công khai.

## 5. Team Module Ownership
- **Nguyễn Nguyên:** Infra, Flyway, Entities/Repositories, Security JWT, Admin CRUD (Hub, Service, User) & Dashboard Chart.js.
- **Lê Đàm Nguyên:** Pricing Engine, Customer Portal, VNPay Sandbox & Tem PDF A6/A5 (OpenPDF + ZXing).
- **Mạnh Tiến:** Shipment State Machine, Staff Portal, Shipper Portal & Cloudflare R2 (POD).
- **Thế Quân:** Public Portal, WebSocket STOMP (Live Tracking, Notify, Chat CSKH) & Quản lý / Đối soát COD.

## 6. Git, Branching, Commit & PR Conventions
- **Branch Naming:**
  - Nhánh mặc định `main` luôn ở trạng thái biên dịch và chạy ổn định; **KHÔNG** push trực tiếp lên `main` khi phát triển tính năng (từ Phase 1 trở đi).
  - Tên nhánh làm việc dùng chữ thường không dấu, phân cách bằng `-`, theo mẫu `<type>/<member>-<short-topic>`:
    - `feat/` — tính năng mới (VD: `feat/damnguyen-vnpay`, `feat/manhtien-r2-pod`, `feat/thequan-websocket`)
    - `fix/` — sửa lỗi (VD: `fix/nguyennguyen-jwt-cookie`)
    - `refactor/` — tối ưu code không đổi nghiệp vụ
    - `docs/` — tài liệu (`docs/`, `README.md`, `AGENTS.md`)
    - `chore/` — cấu hình, build, Docker, tooling
- **Conventional Commits:**
  - Định dạng bắt buộc: `<type>(<scope>): <mô tả ngắn gọn <= 72 ký tự>` (hoặc `<type>: <mô tả>` khi thay đổi phạm vi chung).
  - `type` hợp lệ: `feat`, `fix`, `docs`, `refactor`, `test`, `style`, `chore`.
  - `scope` gợi ý: `infra`, `db`, `auth`, `hub`, `pricing`, `customer`, `shipment`, `vnpay`, `pdf`, `r2`, `ws`, `cod`, `admin`, `public`.
  - Ví dụ: `feat(vnpay): handle return URL and IPN idempotently`, `fix(shipment): prevent cancel after PICKED_UP`.
  - **KHÔNG** viết commit message mơ hồ (`update`, `fix`, `test`, `final`, `done`).
- **Pull Request (PR) Rules:**
  - Tiêu đề PR tuân thủ chuẩn Conventional Commits (`feat(scope): ...`).
  - Mỗi PR chỉ tập trung vào **1 tính năng / 1 sửa lỗi cụ thể**, không gộp nhiều module không liên quan.
  - Trước khi mở PR: cập nhật code mới nhất từ `main`, chạy `mvn test` pass 100%, rà `git status`/`git diff` để chắc chắn không dính `.env`, file IDE hay sửa ngoài phạm vi module.
  - Mô tả PR ngắn gọn gồm: **(1) Thay đổi chính**, **(2) Kết quả test (`mvn test` / kịch bản đã kiểm tra)**, **(3) Ảnh chụp màn hình** (nếu có thay đổi UI).
  - Cần tối thiểu **1 review approve** (ưu tiên Nguyễn Nguyên review tích hợp) trước khi merge vào `main`; xóa nhánh feature sau khi đã merge.

## 7. Verification Workflow
- Luôn chạy `mvn test` (hoặc tối thiểu `mvn test-compile`) sau khi chỉnh sửa code Java để xác nhận không lỗi cú pháp, lỗi mapping và Unit Tests pass.
- Khi thêm/sửa endpoint, trang Thymeleaf hoặc kênh WebSocket, luôn kiểm tra lại phân quyền & chống IDOR theo `docs/08-TEST-PLAN.md`.
- Kiểm tra `git status` trước khi commit để đảm bảo không vô tình staged file `.env` hay file rác IDE.
