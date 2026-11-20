# Đặc tả Yêu cầu Phần mềm (SRS)
## Dự án: Hệ thống Quản lý Chuỗi Giao nhận Logistic — UTEExpress

---

## 1. Tổng quan Hệ thống & Các Tác nhân (Actors)

UTEExpress là ứng dụng web đơn khối phân tầng (**Spring Boot 3 + Thymeleaf + Bootstrap 5**) kết nối **PostgreSQL 16** (chạy qua Docker Compose), xác thực bằng **JWT (HttpOnly Cookie)** và giao tiếp thời gian thực bằng **WebSocket (STOMP/SockJS)**.

### 1.1. Danh sách Tác nhân
* **Guest (Khách vãng lai):** Người dùng chưa đăng nhập (tra cứu vận đơn, ước tính cước, xem mạng lưới bưu cục).
* **Customer (`CUSTOMER`):** Chủ shop / người gửi hàng.
* **Staff (`STAFF`):** Nhân viên vận hành trực thuộc 1 bưu cục (`hub_id`).
* **Shipper (`SHIPPER`):** Nhân viên lấy/giao hàng trực thuộc 1 bưu cục (`hub_id`).
* **Admin (`ADMIN`):** Quản trị viên toàn chuỗi UTEExpress.
* **VNPay Sandbox (External System):** Cổng thanh toán trực tuyến kiểm thử (Return URL & IPN).

### 1.2. Sơ đồ Use Case Tổng quan

```mermaid
flowchart LR
    Guest(["Guest"])
    Customer(["Customer"])
    Staff(["Hub Staff"])
    Shipper(["Shipper"])
    Admin(["Admin"])
    VNPay(["VNPay Sandbox"])

    subgraph Public["Cổng Công khai"]
        UC1["Tra cứu 1-5 vận đơn (Live WS)"]
        UC2["Ước tính cước vận chuyển"]
        UC3["Xem mạng lưới bưu cục"]
    end

    subgraph Auth["Định danh & Hồ sơ"]
        UC4["Đăng ký / Đăng nhập (JWT)"]
        UC5["Quản lý Sổ địa chỉ & Ngân hàng COD"]
    end

    subgraph Order["Vận đơn & Thanh toán"]
        UC6["Tạo vận đơn & Tính cước"]
        UC7["Thanh toán cước online"]
        UC8["Xuất tem PDF A6/A5 (QR + Barcode)"]
        UC9["Hủy vận đơn hợp lệ"]
    end

    subgraph Ops["Vận hành Chuỗi Hub"]
        UC10["Phân công Shipper lấy / giao"]
        UC11["Nhập kho gửi / Xuất trung chuyển / Nhập kho nhận"]
        UC12["Lấy hàng / Giao hàng kèm ảnh POD / Giao thất bại"]
        UC13["Xử lý giao lại / Hoàn hàng"]
    end

    subgraph RealtimeCOD["Realtime & COD"]
        UC14["Nhận thông báo & Chat CSKH (WebSocket)"]
        UC15["Nộp tiền COD từ Shipper về Bưu cục"]
        UC16["Tạo & Duyệt phiếu đối soát COD"]
    end

    subgraph Manage["Quản trị Chuỗi"]
        UC17["Quản lý Hub, Gói cước, Tài khoản"]
        UC18["Xem Dashboard & Biểu đồ thống kê"]
    end

    Guest --> UC1 & UC2 & UC3
    Customer --> UC4 & UC5 & UC6 & UC7 & UC8 & UC9 & UC14
    UC7 --> VNPay
    Staff --> UC4 & UC8 & UC9 & UC10 & UC11 & UC13 & UC14 & UC15
    Shipper --> UC4 & UC12 & UC14 & UC15
    Admin --> UC4 & UC8 & UC14 & UC16 & UC17 & UC18
```

---

## 2. Yêu cầu Chức năng (Functional Requirements)

| Mã FR | Phân hệ | Đặc tả chi tiết |
| :--- | :--- | :--- |
| **FR-01** | **Xác thực & Phân quyền (JWT)** | - Đăng ký tài khoản mới mặc định role `CUSTOMER`, mã hóa mật khẩu bằng `BCrypt`.<br>- Đăng nhập sinh **JWT Access Token** (hạn 24 giờ) chứa `userId`, `email`, `role`, `hubId` và lưu vào **HttpOnly Cookie** (`ACCESS_TOKEN`).<br>- Filter bảo mật đọc JWT từ Cookie (hoặc header `Authorization: Bearer`) để xác thực stateless cho cả trang Thymeleaf, REST API và kết nối WebSocket.<br>- Tài khoản trạng thái `LOCKED` bị chặn đăng nhập ngay lập tức. |
| **FR-02** | **Cổng Công khai (Public)** | - Tra cứu cùng lúc từ 1 đến 5 mã vận đơn (phân cách bởi dấu phẩy). Tự động che thông tin nhạy cảm (SĐT dạng `090****789`, ẩn COD và ảnh POD).<br>- Công cụ ước tính cước công khai cho tất cả gói dịch vụ đang `ACTIVE`.<br>- Danh sách bưu cục UTEExpress có lọc theo Tỉnh/Thành và loại bưu cục. |
| **FR-03** | **Quản lý Hồ sơ, Sổ địa chỉ & Ngân hàng** | - Customer thêm/sửa/xóa/chọn mặc định địa chỉ lấy và giao hàng.<br>- Customer khai báo thông tin ngân hàng nhận tiền COD (`bankName`, `bankAccountNumber`, `bankAccountHolder`). |
| **FR-04** | **Tạo vận đơn & Tính cước tự động** | - Tính trọng lượng quy đổi: nếu có `D, R, C (cm)` thì `volumetricWeight = (D * R * C) / 5000.0`; `chargeableWeight = ceilToHalfKg(max(actualWeight, volumetricWeight))`.<br>- Tự động gán `origin_hub` và `dest_hub` theo tỉnh/khu vực; đánh dấu đơn nội vùng (`origin_hub == dest_hub`) hoặc liên tỉnh (`origin_hub != dest_hub`).<br>- Lưu đầy đủ: thông tin gửi/nhận, hàng hóa, cờ `isFragile`, tùy chọn đồng kiểm (`inspectionPolicy`), gói dịch vụ, cước vận chuyển, phí COD, `payerType` (`SENDER`/`RECEIVER`), `paymentMethod` (`CASH`/`VNPAY`).<br>- Sinh mã vận đơn duy nhất `UTE` + `YYMMDD` + `6 ký tự ngẫu nhiên`. |
| **FR-05** | **Thanh toán cước VNPay Sandbox** | - Với đơn `payerType = SENDER` và `paymentMethod = VNPAY`: tạo đơn trạng thái `PENDING_PAYMENT`, tạo URL thanh toán VNPay API v2.1.0 ký bằng `HMAC-SHA512`.<br>- Xử lý cả `Return URL` và `IPN Endpoint`: xác thực chữ ký, kiểm tra số tiền và mã giao dịch khớp, đảm bảo chỉ cập nhật 1 lần (**idempotent**). Thành công → `paymentStatus = PAID`, `status = CREATED`, ghi tracking log và báo WebSocket tới bưu cục gửi.<br>- Nếu chưa thanh toán, hỗ trợ bấm thanh toán lại hoặc hủy đơn. |
| **FR-06** | **Xuất Tem Vận đơn PDF (A6 / A5)** | - Sinh PDF trên server bằng **OpenPDF + ZXing** (khổ mặc định `A6: 105 × 148 mm`, hỗ trợ `?size=A5`).<br>- Nội dung tem gồm: Logo UTEExpress, gói dịch vụ, **Routing Code** (`originHub.code → destHub.code`), **Barcode Code128**, **QR Code** trỏ tới URL tra cứu, thông tin gửi/nhận, tên hàng & trọng lượng, nhãn `[HÀNG DỄ VỠ]` (nếu có), chỉ dẫn đồng kiểm, **Tổng tiền thu người nhận** và ô chữ ký.<br>- Kiểm tra quyền tải PDF: chỉ chủ đơn, Staff hub liên quan hoặc Admin. |
| **FR-07** | **Điều phối & Luân chuyển Chuỗi Hub** | - Staff bưu cục chỉ thao tác các đơn liên quan tới `hub_id` của mình (`origin_hub`, `dest_hub`, hoặc `current_hub`).<br>- Hỗ trợ trọn vẹn các thao tác: gán Shipper lấy hàng, xác nhận nhập bưu cục gửi, xuất kho trung chuyển liên tỉnh, xác nhận nhập bưu cục nhận, gán Shipper giao hàng, xử lý giao lại (`failed_delivery_count < 3`) hoặc chuyển hoàn (`RETURNING` → `RETURNED`).<br>- Mọi chuyển đổi trạng thái phải qua hàm kiểm tra hợp lệ và ghi 1 dòng vào `shipment_tracking_logs`. |
| **FR-08** | **Tác vụ Shipper & Ảnh POD** | - Giao diện web Bootstrap 5 chia tab *Cần lấy / Cần giao / Đã giao & COD*.<br>- Xác nhận lấy hàng (`ASSIGNED_PICKUP → PICKED_UP`).<br>- Xác nhận giao thành công (`OUT_FOR_DELIVERY → DELIVERED`): **bắt buộc** upload ảnh POD (`JPG/PNG/WEBP`, tối đa `5MB`), đẩy lên **Cloudflare R2** với object key `pod/{UUID}.{ext}` và lưu object key vào DB.<br>- Xác nhận giao thất bại (`OUT_FOR_DELIVERY → DELIVERY_FAILED`): bắt buộc chọn/nhập lý do và tăng `failed_delivery_count`. |
| **FR-09** | **Realtime WebSocket (Tracking, Notify, Chat)** | - Sử dụng Spring WebSocket + STOMP + SockJS với Simple In-Memory Broker (không dùng broker ngoài).<br>- **Live Tracking:** phát sự kiện cập nhật tới `/topic/tracking/{trackingCode}` mỗi khi trạng thái đơn thay đổi.<br>- **Thông báo đẩy:** lưu vào bảng `notifications` và đẩy tới `/user/queue/notifications` cho người nhận liên quan.<br>- **Chat CSKH:** lưu tin nhắn vào `chat_messages` và đẩy tới `/topic/chat/{shipmentId}` (chặn quyền truy cập nếu không thuộc chủ đơn / Staff hub liên quan / Admin). |
| **FR-10** | **Quản lý COD & Quản trị Chuỗi (Admin)** | - Khi đơn `DELIVERED` có `COD > 0` (hoặc người nhận trả cước): đánh dấu `codStatus = HELD_BY_SHIPPER` → Staff xác nhận thu tiền từ Shipper (`COLLECTED_BY_HUB`) → Admin tạo **Phiếu đối soát (`cod_settlements`)** tự động cấn trừ cước chưa thanh toán → Duyệt đã chuyển khoản (`SETTLED`).<br>- Admin quản lý CRUD chuỗi `Hub`, `ServiceType`, `User` (gán `hub_id` cho Staff/Shipper, khóa/mở tài khoản) và xem Dashboard thống kê (KPI + biểu đồ Chart.js lọc theo khoảng ngày). |

---

## 3. Yêu cầu Phi chức năng (Non-Functional Requirements)

| Mã NFR | Tiêu chí | Yêu cầu cụ thể (Thực tế, không over-engineering) |
| :--- | :--- | :--- |
| **NFR-01** | **Bảo mật (Security)** | Mật khẩu băm bằng `BCrypt`; JWT ký `HS256` bằng secret tối thiểu 256-bit nạp từ biến môi trường `.env`; Cookie gắn cờ `HttpOnly`, `SameSite=Lax`; kiểm tra phân quyền ở cả URL filter và tầng Service (chặn IDOR — xem/sửa đơn của người khác hoặc hub khác). |
| **NFR-02** | **Toàn vẹn dữ liệu** | Mọi nghiệp vụ ghi nhiều bảng (tạo đơn + tracking log; đổi trạng thái + log + thông báo; chốt đối soát COD + cập nhật các vận đơn) phải bọc trong `@Transactional`. Tiền tệ dùng `BigDecimal` (`NUMERIC(15,0)` hoặc `NUMERIC(15,2)` trong PostgreSQL). |
| **NFR-03** | **An toàn Upload File (POD)** | Chỉ chấp nhận ảnh `image/jpeg`, `image/png`, `image/webp`; giới hạn tối đa `5MB`; đổi tên file bằng `UUID` để chống ghi đè và chống path traversal (`../`). |
| **NFR-04** | **Hiệu năng & Trải nghiệm Web** | Phân trang (`Pageable`) cho tất cả danh sách vận đơn, đối soát và người dùng; tạo index PostgreSQL cho các cột tra cứu thường xuyên (`tracking_code`, `status`, `customer_id`, `current_hub_id`). Giao diện sử dụng Bootstrap 5 chuẩn responsive, chạy ổn định trên trình duyệt Chrome/Edge hiện đại. |
| **NFR-05** | **Khả năng triển khai & Bảo trì** | Database PostgreSQL 16 chạy bằng 1 lệnh `docker compose up -d` (chỉ 1 service DB gọn nhẹ); ảnh POD lưu tập trung trên Cloudflare R2; schema & dữ liệu mẫu quản lý bằng **Flyway**; cấu trúc code theo mô hình phân tầng chuẩn Spring Boot (`controller`, `service`, `repository`, `entity`, `dto`, `config`). |

---

## 4. Máy Trạng thái Vận đơn (Shipment State Machine)

### 4.1. Sơ đồ Chuyển đổi Trạng thái

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT : Tạo đơn (VNPay)
    [*] --> CREATED : Tạo đơn (Tiền mặt / Người nhận trả)

    PENDING_PAYMENT --> CREATED : VNPay thành công
    PENDING_PAYMENT --> CANCELLED : Hủy đơn

    CREATED --> ASSIGNED_PICKUP : Staff gán Shipper lấy
    CREATED --> CANCELLED : Hủy đơn

    ASSIGNED_PICKUP --> PICKED_UP : Shipper đã lấy hàng
    ASSIGNED_PICKUP --> CANCELLED : Hủy đơn

    PICKED_UP --> IN_ORIGIN_HUB : Staff nhập kho gửi

    IN_ORIGIN_HUB --> IN_TRANSIT : Xuất trung chuyển (Liên tỉnh)
    IN_ORIGIN_HUB --> IN_DEST_HUB : Chuyển sẵn sàng giao (Nội vùng)

    IN_TRANSIT --> IN_DEST_HUB : Staff bưu cục nhận nhập kho

    IN_DEST_HUB --> OUT_FOR_DELIVERY : Staff gán Shipper giao

    OUT_FOR_DELIVERY --> DELIVERED : Shipper giao thành công (+ Ảnh POD)
    OUT_FOR_DELIVERY --> DELIVERY_FAILED : Shipper báo giao thất bại (+ Lý do)

    DELIVERY_FAILED --> OUT_FOR_DELIVERY : Giao lại (failed_count < 3)
    DELIVERY_FAILED --> RETURNING : Kích hoạt hoàn hàng

    RETURNING --> RETURNED : Xác nhận đã hoàn về người gửi

    DELIVERED --> [*]
    RETURNED --> [*]
    CANCELLED --> [*]
```

### 4.2. Bảng Quy định Quyền & Điều kiện Chuyển Trạng thái

> *Cài đặt gọn nhẹ trong Java bằng `Map<ShipmentStatus, Set<ShipmentStatus>>` ở tầng Service (không dùng thư viện state machine phức tạp).*

| Trạng thái hiện tại | Trạng thái đích hợp lệ | Ai được thực hiện | Điều kiện / Hành động đi kèm |
| :--- | :--- | :--- | :--- |
| `PENDING_PAYMENT` | `CREATED` | Hệ thống (VNPay callback) | Xác thực chữ ký HMAC-SHA512 thành công → `paymentStatus = PAID`. |
| `PENDING_PAYMENT` | `CANCELLED` | Customer (chủ đơn), Staff, Admin | Không cần hoàn tiền vì chưa thanh toán. |
| `CREATED` | `ASSIGNED_PICKUP` | Staff (`origin_hub`), Admin | Chọn `pickup_shipper_id` thuộc `origin_hub`. |
| `CREATED` | `CANCELLED` | Customer (chủ đơn), Staff, Admin | Nếu `paymentStatus == PAID` → chuyển `REFUND_PENDING`. |
| `ASSIGNED_PICKUP` | `PICKED_UP` | Shipper (`pickup_shipper_id`) | Ghi thời điểm lấy hàng. |
| `ASSIGNED_PICKUP` | `CANCELLED` | Customer (chủ đơn), Staff, Admin | Nếu `paymentStatus == PAID` → chuyển `REFUND_PENDING`. |
| `PICKED_UP` | `IN_ORIGIN_HUB` | Staff (`origin_hub`), Admin | Cập nhật `current_hub = origin_hub`. |
| `IN_ORIGIN_HUB` | `IN_TRANSIT` | Staff (`origin_hub`), Admin | Chỉ áp dụng khi `origin_hub != dest_hub`. |
| `IN_ORIGIN_HUB` | `IN_DEST_HUB` | Staff (`origin_hub`), Admin | Chỉ áp dụng khi `origin_hub == dest_hub` (đơn nội vùng). |
| `IN_TRANSIT` | `IN_DEST_HUB` | Staff (`dest_hub`), Admin | Cập nhật `current_hub = dest_hub`. |
| `IN_DEST_HUB` | `OUT_FOR_DELIVERY` | Staff (`dest_hub`), Admin | Chọn `delivery_shipper_id` thuộc `dest_hub`. |
| `OUT_FOR_DELIVERY` | `DELIVERED` | Shipper (`delivery_shipper_id`) | **Bắt buộc** có ảnh POD. Nếu có tiền thu hộ/thu cước → `codStatus = HELD_BY_SHIPPER`. |
| `OUT_FOR_DELIVERY` | `DELIVERY_FAILED` | Shipper (`delivery_shipper_id`) | Bắt buộc ghi lý do; tăng `failed_delivery_count += 1`. |
| `DELIVERY_FAILED` | `OUT_FOR_DELIVERY` | Staff (`dest_hub`), Admin | Chỉ cho phép khi `failed_delivery_count < 3`. |
| `DELIVERY_FAILED` | `RETURNING` | Staff (`dest_hub`), Admin | Khi `failed_delivery_count >= 3` hoặc người nhận từ chối nhận. |
| `RETURNING` | `RETURNED` | Staff (`origin_hub`/`dest_hub`), Admin | Hoàn tất trả hàng cho người gửi. |

### 4.3. Trạng thái Thanh toán Cước & Trạng thái COD
* **`PaymentStatus`:** `UNPAID` (Chưa thanh toán) | `PAID` (Đã thanh toán) | `REFUND_PENDING` (Chờ hoàn tiền) | `REFUNDED` (Đã hoàn tiền).
* **`CodStatus`:** `NONE` (Không có COD) | `PENDING` (Đang giao, chưa thu) | `HELD_BY_SHIPPER` (Shipper đã thu, đang giữ) | `COLLECTED_BY_HUB` (Shipper đã nộp về bưu cục) | `SETTLED` (Đã đối soát chuyển khoản cho khách).

---

## 5. Sơ đồ Tuần tự (Sequence Diagrams)

### 5.1. Đăng nhập & Truy cập Trang Thymeleaf bằng JWT Cookie

```mermaid
sequenceDiagram
    actor User as Người dùng (Trình duyệt)
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthService
    participant DB as PostgreSQL
    participant Filter as JwtAuthenticationFilter
    participant ViewCtrl as PortalController (Thymeleaf)

    User->>AuthCtrl: POST /auth/login (email, password)
    AuthCtrl->>AuthSvc: authenticate(email, password)
    AuthSvc->>DB: Tìm user theo email & kiểm tra trạng thái ACTIVE
    DB-->>AuthSvc: UserEntity (passwordHash, role, hubId)
    AuthSvc->>AuthSvc: Kiểm tra BCrypt + Sinh JWT Access Token
    AuthSvc-->>AuthCtrl: jwtToken + role
    AuthCtrl-->>User: 302 Redirect tới Dashboard theo role + Set-Cookie(ACCESS_TOKEN, HttpOnly)

    User->>Filter: GET /customer/shipments (tự động gửi Cookie ACCESS_TOKEN)
    Filter->>Filter: Giải mã & xác thực chữ ký JWT + hạn dùng
    Filter->>ViewCtrl: Nạp Authentication vào SecurityContext
    ViewCtrl->>DB: Truy vấn danh sách vận đơn của user
    DB-->>ViewCtrl: Page<Shipment>
    ViewCtrl-->>User: Render trang HTML Thymeleaf
```

### 5.2. Tạo vận đơn & Thanh toán Cước qua VNPay Sandbox

```mermaid
sequenceDiagram
    actor Cust as Customer
    participant ShipCtrl as ShipmentController
    participant PaySvc as VnPayService
    participant DB as PostgreSQL
    participant VNPay as VNPay Sandbox
    participant WS as WebSocket (STOMP)

    Cust->>ShipCtrl: POST /customer/shipments (payer=SENDER, method=VNPAY)
    ShipCtrl->>DB: Lưu Shipment (status=PENDING_PAYMENT, paymentStatus=UNPAID)
    ShipCtrl->>PaySvc: createPaymentUrl(shipment) [ký HMAC-SHA512]
    PaySvc-->>ShipCtrl: vnpayUrl
    ShipCtrl-->>Cust: 302 Redirect sang trang VNPay Sandbox

    Cust->>VNPay: Nhập thông tin thẻ test & xác nhận OTP
    VNPay-->>Cust: Redirect về GET /payment/vnpay-return?vnp_...
    Cust->>ShipCtrl: GET /payment/vnpay-return?vnp_...
    ShipCtrl->>PaySvc: verifySignatureAndProcess(params)
    alt Chữ ký hợp lệ & ResponseCode == "00" & Chưa PAID
        PaySvc->>DB: Cập nhật paymentStatus=PAID, status=CREATED + Ghi tracking log
        PaySvc->>WS: Gửi thông báo đơn mới tới Staff của origin_hub
        PaySvc-->>ShipCtrl: Thành công
        ShipCtrl-->>Cust: Hiển thị trang thanh toán thành công + Nút In Tem PDF
    else Thất bại hoặc Hủy giao dịch
        PaySvc-->>ShipCtrl: Thất bại
        ShipCtrl-->>Cust: Hiển thị lỗi + Nút Thanh toán lại / Hủy đơn
    end
```

### 5.3. Shipper Xác nhận Giao thành công (POD) & Đẩy Live Tracking WebSocket

```mermaid
sequenceDiagram
    actor Shipper as Shipper (Web)
    participant Ctrl as ShipperController
    participant R2Svc as R2StorageService
    participant ShipSvc as ShipmentService
    participant DB as PostgreSQL
    participant WS as SimpMessagingTemplate
    actor Tracker as Khách / Public Tracking

    Shipper->>Ctrl: POST /shipper/shipments/{id}/deliver (multipart: podImage, note)
    Ctrl->>R2Svc: validate & upload ảnh POD lên Cloudflare R2 (pod/uuid.jpg)
    R2Svc-->>Ctrl: podImageKey (pod/uuid.jpg)
    Ctrl->>ShipSvc: markDelivered(id, shipper, podImageKey, note)
    ShipSvc->>DB: Kiểm tra trạng thái & quyền → Cập nhật DELIVERED, codStatus, podImageKey + Ghi tracking log
    DB-->>ShipSvc: Shipment đã cập nhật
    ShipSvc->>WS: convertAndSend("/topic/tracking/" + trackingCode, payload)
    ShipSvc->>WS: convertAndSendToUser(customerEmail, "/queue/notifications", notify)
    WS-->>Tracker: Cập nhật timeline & badge trạng thái tức thì (không cần F5)
    Ctrl-->>Shipper: Thông báo giao hàng thành công
```

### 5.4. Quy trình Nộp tiền & Đối soát COD

```mermaid
sequenceDiagram
    actor Staff as Staff Bưu cục nhận
    actor Admin as Admin
    actor Cust as Customer
    participant CodSvc as CodSettlementService
    participant DB as PostgreSQL
    participant WS as WebSocket

    Staff->>CodSvc: Xác nhận thu COD từ Shipper (danh sách shipmentIds)
    CodSvc->>DB: Cập nhật codStatus = COLLECTED_BY_HUB

    Admin->>CodSvc: Tạo phiếu đối soát COD cho Customer
    CodSvc->>DB: Lấy các đơn DELIVERED + COLLECTED_BY_HUB chưa đối soát
    CodSvc->>CodSvc: Tính NetAmount = Tổng COD - Tổng cước người gửi chưa trả
    CodSvc->>DB: Lưu CodSettlement (status=PENDING) & gắn settlement_id cho các đơn

    Admin->>CodSvc: Xác nhận đã chuyển khoản (mã tham chiếu giao dịch)
    CodSvc->>DB: Cập nhật CodSettlement=SETTLED & các vận đơn codStatus=SETTLED
    CodSvc->>WS: Thông báo realtime cho Customer
    WS-->>Cust: Nhận thông báo "Phiếu đối soát COD đã được thanh toán"
```
