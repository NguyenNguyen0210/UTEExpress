# Đặc tả Routes, REST API & WebSocket Contract
## Dự án: Hệ thống Quản lý Chuỗi Giao nhận Logistic — UTEExpress

---

## 1. Quy ước Chung

* **Web MVC Routes (Thymeleaf SSR):** Phục vụ toàn bộ trang giao diện và xử lý form (`GET` / `POST` kèm Redirect-After-Post và Flash Message).
* **REST API (`/api/v1/**`):** Chỉ sử dụng cho các tác vụ bất đồng bộ (Fetch API: tính cước tức thì, thông báo, lịch sử chat, dữ liệu Chart.js), webhook VNPay IPN và endpoint đăng nhập JWT cho Postman/test.
* **WebSocket (`/ws`):** Giao tiếp STOMP qua SockJS dùng chung phiên xác thực Cookie `ACCESS_TOKEN`.
* **Quy ước định dạng:** JSON dùng `camelCase`, thời gian chuẩn ISO-8601 (`2026-11-20T09:30:00+07:00`), tiền tệ là số nguyên VNĐ.

---

## 2. Danh sách Web MVC Routes (Thymeleaf)

### 2.1. Cổng Công khai & Định danh (`Public` / `Auth`)
| Method | URL | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Public | Trang chủ UTEExpress (ô tra cứu nhanh, giới thiệu gói dịch vụ) |
| `GET` | `/tracking?codes=UTE...,UTE...` | Public | Tra cứu 1–5 mã vận đơn (che SĐT/tên, có Live Tracking WebSocket) |
| `GET` | `/estimate` | Public | Trang công cụ ước tính cước vận chuyển |
| `GET` | `/hubs?province=&type=` | Public | Tra cứu mạng lưới chuỗi bưu cục & kho trung chuyển |
| `GET` | `/auth/login` | Public | Trang đăng nhập |
| `POST` | `/auth/login` | Public | Xác thực, cấp JWT vào `HttpOnly Cookie`, điều hướng theo role |
| `GET` | `/auth/register` | Public | Trang đăng ký tài khoản `CUSTOMER` |
| `POST` | `/auth/register` | Public | Tạo tài khoản mới |
| `POST` | `/auth/logout` | Đã đăng nhập | Xóa cookie `ACCESS_TOKEN` và chuyển về `/auth/login` |

### 2.2. Thanh toán, Tem PDF & Ảnh POD (Dùng chung có kiểm tra quyền)
| Method | URL | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/payment/vnpay-return` | Public (VNPay redirect) | Xác thực chữ ký `HMAC-SHA512`, cập nhật `PAID` → `CREATED` (idempotent), hiển thị kết quả thanh toán |
| `GET` | `/shipments/{id}/waybill?size=A6` | Chủ đơn, Staff hub liên quan, Admin | Xuất & tải/xem file PDF tem vận đơn khổ `A6` (mặc định) hoặc `A5` có Barcode + QR + Routing Code |
| `GET` | `/files/pod/{shipmentId}` | Chủ đơn, Shipper giao đơn, Staff hub liên quan, Admin | Stream ảnh POD từ **Cloudflare R2** (bucket private) kèm đúng `Content-Type` |

### 2.3. Cổng Khách hàng (`/customer/**` — Role: `CUSTOMER`)
| Method | URL | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/customer` | Tổng quan của khách (số đơn theo trạng thái, tổng COD chờ đối soát, đơn gần đây) |
| `GET` | `/customer/shipments?status=&keyword=&from=&to=&page=` | Danh sách vận đơn (tab trạng thái có badge đếm, tìm kiếm, phân trang) |
| `GET` | `/customer/shipments/new` | Form tạo vận đơn mới (2 cột + sticky tổng cước) |
| `POST` | `/customer/shipments` | Tạo vận đơn (nếu chọn VNPay → redirect sang VNPay Sandbox) |
| `GET` | `/customer/shipments/{id}` | Chi tiết vận đơn (timeline realtime, ảnh POD, nút In tem PDF, khung Chat CSKH) |
| `POST` | `/customer/shipments/{id}/cancel` | Hủy vận đơn hợp lệ (`PENDING_PAYMENT`, `CREATED`, `ASSIGNED_PICKUP`) |
| `POST` | `/customer/shipments/{id}/retry-vnpay` | Tạo lại URL thanh toán VNPay cho đơn `PENDING_PAYMENT` |
| `GET` | `/customer/addresses` | Danh sách sổ địa chỉ |
| `POST` | `/customer/addresses` | Thêm mới địa chỉ |
| `POST` | `/customer/addresses/{id}/default` | Đặt địa chỉ mặc định |
| `POST` | `/customer/addresses/{id}/delete` | Xóa địa chỉ |
| `GET` | `/customer/cod` | Lịch sử phiếu đối soát COD & chi tiết từng phiếu |
| `GET` | `/customer/profile` | Xem & cập nhật hồ sơ cá nhân + thông tin ngân hàng nhận COD |
| `POST` | `/customer/profile` | Lưu thông tin hồ sơ & tài khoản ngân hàng |

### 2.4. Cổng Nhân viên Bưu cục (`/staff/**` — Role: `STAFF`, `ADMIN`)
| Method | URL | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/staff` | Dashboard bưu cục (đơn chờ lấy, hàng đang tại kho, hàng sắp đến, COD cần thu) |
| `GET` | `/staff/shipments?tab=&keyword=&page=` | Danh sách vận đơn thuộc phạm vi bưu cục theo tab nghiệp vụ |
| `GET` | `/staff/shipments/{id}` | Chi tiết vận đơn + nút thao tác chuyển trạng thái hợp lệ + Chat CSKH |
| `POST` | `/staff/shipments/{id}/assign-pickup` | Gán Shipper lấy hàng (`CREATED → ASSIGNED_PICKUP`) |
| `POST` | `/staff/shipments/{id}/arrive-origin` | Xác nhận nhập bưu cục gửi (`PICKED_UP → IN_ORIGIN_HUB`; nếu nội vùng cho chuyển sẵn sàng giao) |
| `POST` | `/staff/shipments/{id}/dispatch-transit` | Xuất kho trung chuyển liên tỉnh (`IN_ORIGIN_HUB → IN_TRANSIT`) |
| `POST` | `/staff/shipments/{id}/arrive-dest` | Xác nhận nhập bưu cục nhận (`IN_TRANSIT` hoặc `IN_ORIGIN_HUB` → `IN_DEST_HUB`) |
| `POST` | `/staff/shipments/{id}/assign-delivery` | Gán Shipper giao hàng (`IN_DEST_HUB` hoặc `DELIVERY_FAILED` → `OUT_FOR_DELIVERY`) |
| `POST` | `/staff/shipments/{id}/start-return` | Kích hoạt hoàn hàng (`DELIVERY_FAILED → RETURNING`) |
| `POST` | `/staff/shipments/{id}/complete-return` | Xác nhận hoàn tất trả hàng (`RETURNING → RETURNED`) |
| `POST` | `/staff/shipments/{id}/cancel` | Hủy đơn hợp lệ trước khi lấy hàng |
| `GET` | `/staff/cod` | Danh sách đơn `HELD_BY_SHIPPER` theo từng Shipper thuộc bưu cục |
| `POST` | `/staff/cod/collect` | Xác nhận thu tiền COD từ Shipper (`HELD_BY_SHIPPER → COLLECTED_BY_HUB`) |

### 2.5. Cổng Giao nhận (`/shipper/**` — Role: `SHIPPER`)
| Method | URL | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/shipper/tasks?tab=pickup\|delivery\|done` | Trang tác vụ chính (3 tab: *Cần lấy* / *Cần giao* / *Đã xử lý*) |
| `POST` | `/shipper/shipments/{id}/pickup` | Xác nhận đã lấy hàng (`ASSIGNED_PICKUP → PICKED_UP`) |
| `POST` | `/shipper/shipments/{id}/deliver` | Giao thành công (`multipart/form-data`: bắt buộc `podImage` + `note`) → upload Cloudflare R2 & chuyển `DELIVERED` |
| `POST` | `/shipper/shipments/{id}/fail` | Báo giao thất bại (bắt buộc `reason`) → `DELIVERY_FAILED`, tăng `failed_delivery_count` |
| `GET` | `/shipper/cod` | Xem danh sách & tổng tiền COD đang giữ chưa nộp về bưu cục |

### 2.6. Cổng Quản trị Chuỗi (`/admin/**` — Role: `ADMIN`)
| Method | URL | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/admin` | Dashboard điều hành toàn chuỗi (thẻ KPI + biểu đồ Chart.js lọc theo khoảng ngày) |
| `GET` | `/admin/hubs` | Danh sách chuỗi bưu cục / kho trung chuyển |
| `POST` | `/admin/hubs` | Thêm mới hoặc cập nhật bưu cục |
| `POST` | `/admin/hubs/{id}/toggle-active` | Bật / tắt trạng thái hoạt động bưu cục |
| `GET` | `/admin/services` | Danh sách gói dịch vụ & bảng giá |
| `POST` | `/admin/services` | Thêm mới hoặc cập nhật gói dịch vụ |
| `POST` | `/admin/services/{id}/toggle-active` | Bật / tắt gói dịch vụ |
| `GET` | `/admin/users?role=&hubId=&keyword=` | Quản lý tài khoản toàn hệ thống |
| `POST` | `/admin/users` | Tạo mới tài khoản `STAFF` / `SHIPPER` (bắt buộc chọn `hubId`) |
| `POST` | `/admin/users/{id}/toggle-status` | Khóa (`LOCKED`) hoặc Mở khóa (`ACTIVE`) tài khoản |
| `GET` | `/admin/cod-settlements` | Danh sách phiếu đối soát COD & khách hàng có đơn sẵn sàng đối soát |
| `POST` | `/admin/cod-settlements` | Tạo phiếu đối soát COD cho 1 khách hàng (tự cấn trừ cước chưa thanh toán) |
| `POST` | `/admin/cod-settlements/{id}/settle` | Xác nhận đã chuyển khoản (`PENDING → SETTLED`, kèm `transactionRef`) |

---

## 3. Danh sách REST API Endpoints (`/api/v1/**`)

| Method | Endpoint | Quyền | Mục đích |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Public | Đăng nhập trả về JWT JSON (phục vụ test API / Postman) |
| `POST` | `/api/v1/public/estimate` | Public | Tính cước vận chuyển tức thì cho 1 hoặc tất cả gói dịch vụ |
| `GET` | `/api/v1/public/tracking?codes=...` | Public | Trả JSON hành trình cho 1–5 mã vận đơn (đã che thông tin nhạy cảm) |
| `GET` | `/api/v1/payment/vnpay-ipn` | Public (VNPay server) | Nhận webhook IPN từ VNPay, trả JSON chuẩn `{ "RspCode", "Message" }` |
| `GET` | `/api/v1/notifications` | Đã đăng nhập | Lấy 20 thông báo gần nhất + tổng số chưa đọc (`unreadCount`) |
| `POST` | `/api/v1/notifications/{id}/read` | Đã đăng nhập | Đánh dấu 1 thông báo đã đọc |
| `POST` | `/api/v1/notifications/read-all` | Đã đăng nhập | Đánh dấu tất cả thông báo đã đọc |
| `GET` | `/api/v1/chat/{shipmentId}/messages` | Chủ đơn / Staff hub liên quan / Admin | Lấy lịch sử tin nhắn CSKH của vận đơn |
| `GET` | `/api/v1/admin/dashboard/charts?from=&to=` | `ADMIN` | Trả dữ liệu tổng hợp cho 3 biểu đồ Chart.js (Doanh thu & sản lượng theo ngày, Tỷ lệ trạng thái, Sản lượng theo Hub) |

### 3.1. Ví dụ Payload REST API chính

**Request `POST /api/v1/public/estimate`:**
```json
{
  "senderProvince": "TP. Hồ Chí Minh",
  "receiverProvince": "Hà Nội",
  "actualWeightKg": 1.2,
  "lengthCm": 30,
  "widthCm": 20,
  "heightCm": 15,
  "codAmount": 500000,
  "serviceTypeId": null
}
```

**Response `200 OK`:**
```json
{
  "interProvince": true,
  "originHubCode": "HCM-TD01",
  "destHubCode": "HN-CG01",
  "volumetricWeightKg": 1.80,
  "chargeableWeightKg": 2.00,
  "options": [
    {
      "serviceTypeId": 1,
      "code": "STANDARD",
      "name": "Tiêu chuẩn",
      "shippingFee": 40000,
      "codFee": 5000,
      "totalFee": 45000,
      "estimatedDays": "3 - 5 ngày"
    }
  ]
}
```

### 3.2. Chuẩn JSON Trả Lỗi (`ApiErrorResponse`)
```json
{
  "timestamp": "2026-11-20T09:30:00+07:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Không thể hủy vận đơn sau khi shipper đã lấy hàng",
  "fieldErrors": {
    "actualWeightKg": "Trọng lượng phải lớn hơn 0"
  }
}
```

---

## 4. Đặc tả WebSocket Contract (STOMP over SockJS)

* **Endpoint kết nối:** `/ws`

### 4.1. Live Tracking (`/topic/tracking/{trackingCode}`)
* **Chiều:** Server → Client (Public & Đã đăng nhập đều subscribe được).
* **Kích hoạt:** Mỗi khi vận đơn chuyển trạng thái thành công.
* **Payload:**
```json
{
  "trackingCode": "UTE261120A8K4P2",
  "status": "IN_DEST_HUB",
  "statusLabel": "Đã nhập bưu cục nhận",
  "currentHubName": "Bưu cục Cầu Giấy (HN-CG01)",
  "logTitle": "Đã nhập kho Bưu cục Cầu Giấy",
  "logDescription": "Kiện hàng đã đến bưu cục phát, chuẩn bị giao",
  "timestamp": "2026-11-20T14:20:00+07:00"
}
```

### 4.2. Thông báo Đẩy Cá nhân (`/user/queue/notifications`)
* **Chiều:** Server → User cụ thể (xác định qua `Principal.getName()` = email).
* **Payload:**
```json
{
  "id": 105,
  "title": "Đơn hàng mới cần điều phối",
  "message": "Vận đơn UTE261120A8K4P2 vừa được tạo tại Bưu cục Thủ Đức",
  "linkUrl": "/staff/shipments/12",
  "createdAt": "2026-11-20T09:35:00+07:00"
}
```

### 4.3. Chat Hỗ trợ CSKH (`/app/chat/{shipmentId}` ↔ `/topic/chat/{shipmentId}`)
* **Client gửi lên `/app/chat/{shipmentId}`:**
```json
{
  "content": "Shop nhờ bưu cục giao giúp vào buổi chiều sau 14h ạ."
}
```
* **Server lưu DB & broadcast tới `/topic/chat/{shipmentId}`:**
```json
{
  "id": 42,
  "shipmentId": 12,
  "senderId": 6,
  "senderName": "Shop Hoàng Nam",
  "senderRole": "CUSTOMER",
  "content": "Shop nhờ bưu cục giao giúp vào buổi chiều sau 14h ạ.",
  "createdAt": "2026-11-20T10:12:00+07:00"
}
```
