# Kế hoạch Kiểm thử (Test Plan & Test Cases)
## Dự án: Hệ thống Quản lý Chuỗi Giao nhận Logistic — UTEExpress

---

## 1. Chiến lược Kiểm thử (Thực dụng, Không Over-Engineering)

Kiểm thử tập trung vào **2 lớp chính** để đảm bảo độ tin cậy cao mà không tốn thời gian viết test thừa:

1. **Automated Unit Tests (`mvn test` — JUnit 5 + Mockito):** Chỉ viết cho 4 khối logic cốt lõi dễ sai sót:
   * `PricingServiceTest` — công thức tính cước, trọng lượng quy đổi `(D×R×C)/5000`, làm tròn `0.5 kg`, phụ phí liên tỉnh và phí COD.
   * `ShipmentStateTransitionTest` — kiểm tra tất cả chuyển đổi trạng thái hợp lệ và chặn chuyển đổi nhảy cóc/sai quy tắc.
   * `JwtTokenProviderTest` — sinh token, đọc claims (`uid`, `role`, `hubId`), từ chối token hết hạn hoặc sai chữ ký.
   * `VnPayServiceTest` — tạo và xác thực chữ ký `HMAC-SHA512`.
2. **Manual End-to-End & Security Verification:** Thực hiện trực tiếp trên trình duyệt (2 cửa sổ đăng nhập 2 vai trò song song) + Postman cho REST API trước khi chốt từng phase và trước buổi bảo vệ.

---

## 2. Ma trận Phân quyền (Role × Access Control Matrix)

| Đường dẫn / Chức năng | Guest | Customer | Staff | Shipper | Admin | Ghi chú kiểm tra chống IDOR |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| `/`, `/tracking`, `/estimate`, `/hubs` | ✅ | ✅ | ✅ | ✅ | ✅ | `/tracking` phải che SĐT, tên, ẩn COD & ảnh POD |
| `/customer/**` | ❌ (302) | ✅ | ❌ (403) | ❌ (403) | ❌ (403) | Customer chỉ xem/hủy đơn có `customer_id == currentUser.id` |
| `/staff/**` | ❌ (302) | ❌ (403) | ✅ | ❌ (403) | ✅ | Staff chỉ thao tác đơn thuộc `origin/dest/current_hub == user.hubId` |
| `/shipper/**` | ❌ (302) | ❌ (403) | ❌ (403) | ✅ | ❌ (403) | Shipper chỉ cập nhật đơn được gán `pickup/delivery_shipper_id == user.id` |
| `/admin/**` | ❌ (302) | ❌ (403) | ❌ (403) | ❌ (403) | ✅ | Toàn quyền quản trị chuỗi |
| `GET /shipments/{id}/waybill` | ❌ | ✅* | ✅* | ❌ | ✅ | `*` Chỉ chủ đơn hoặc Staff hub liên quan |
| `GET /files/pod/{shipmentId}` | ❌ | ✅* | ✅* | ✅* | ✅ | `*` Chỉ chủ đơn, Shipper giao đơn hoặc Staff hub liên quan |
| `GET /api/v1/chat/{id}/messages` & WS Chat | ❌ (401) | ✅* | ✅* | ❌ (403) | ✅ | `*` Chỉ chủ đơn hoặc Staff hub liên quan |
| `GET /api/v1/admin/dashboard/charts` | ❌ (401) | ❌ (403) | ❌ (403) | ❌ (403) | ✅ | Trả JSON lỗi thay vì redirect HTML |

---

## 3. Bộ Test Cases Trọng tâm (Happy Path & Edge Cases)

### 3.1. Xác thực & Phân quyền (`TC-AUTH`)
| Mã TC | Kịch bản kiểm thử | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| **TC-AUTH-01** | Đăng nhập bằng `shop@uteexpress.vn` / `123456` | Trả cookie `ACCESS_TOKEN` có cờ `HttpOnly`, redirect sang `/customer`, thanh topbar hiển thị đúng tên & role. |
| **TC-AUTH-02** | Admin khóa tài khoản Customer (`LOCKED`), sau đó Customer đăng nhập | Từ chối đăng nhập, hiển thị thông báo tài khoản đã bị khóa; không cấp cookie JWT. |
| **TC-AUTH-03** | Customer A truy cập `/customer/shipments/{id_cua_B}` hoặc tải `/files/pod/{id_cua_B}` | Trả `403 Forbidden` (không lộ dữ liệu đơn của người khác). |
| **TC-AUTH-04** | Chưa đăng nhập gọi `GET /api/v1/notifications` bằng Postman/Fetch | Trả HTTP `401` kèm JSON `ApiErrorResponse` (không redirect về trang HTML login). |
| **TC-AUTH-05** | Đăng xuất rồi bấm nút Back hoặc truy cập lại `/customer` | Cookie `ACCESS_TOKEN` đã bị xóa, hệ thống chuyển hướng về `/auth/login`. |

### 3.2. Tính cước & Trọng lượng Quy đổi (`TC-PRICE`)
| Mã TC | Kịch bản kiểm thử | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| **TC-PRICE-01** | Đơn nội tỉnh (TP.HCM → TP.HCM), gói `STANDARD`, `actual = 0.8 kg`, không nhập `D×R×C`, `COD = 0` | `chargeableWeight = 1.0 kg`, không phụ phí liên tỉnh, `totalFee = 20.000 đ`. |
| **TC-PRICE-02** | Đơn liên tỉnh (TP.HCM → Hà Nội), gói `STANDARD`, `actual = 1.0 kg`, kích thước `30×20×15 cm`, `COD = 500.000 đ` | `volumetric = 9000 / 5000 = 1.8 kg` → làm tròn lên `2.0 kg` (vượt 1kg = 2 bước 0.5kg = `+10.000`), liên tỉnh `+10.000`, phí COD `1% = 5.000` → `shippingFee = 40.000`, `codFee = 5.000`, `totalFee = 45.000 đ`. |
| **TC-PRICE-03** | Admin đổi giá gói `STANDARD` sau khi đơn đã tạo | Giá các đơn cũ giữ nguyên, chỉ đơn tạo mới áp dụng bảng giá mới. |

### 3.3. Thanh toán VNPay Sandbox (`TC-VNPAY`)
| Mã TC | Kịch bản kiểm thử | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| **TC-VNPAY-01** | Tạo đơn `SENDER` + `VNPAY` → Thanh toán thành công bằng thẻ test VNPay | Đơn chuyển `PENDING_PAYMENT → CREATED`, `paymentStatus = PAID`, lưu `vnp_transaction_no`, có tracking log và bắn thông báo tới Staff bưu cục gửi. |
| **TC-VNPAY-02** | Hủy thanh toán tại màn hình VNPay Sandbox | Redirect về trang thông báo chưa hoàn tất, đơn vẫn giữ `PENDING_PAYMENT`, hiển thị nút *"Thanh toán lại"* và *"Hủy đơn"*. |
| **TC-VNPAY-03** | Tải lại (F5) URL `/payment/vnpay-return?...` hoặc gọi IPN lặp lại cho đơn đã `PAID` | Xử lý **idempotent**: không ghi trùng tracking log, không gửi trùng thông báo. |
| **TC-VNPAY-04** | Sửa tay tham số `vnp_Amount` trên URL return để giả mạo chữ ký | Xác thực `HMAC-SHA512` thất bại, từ chối cập nhật đơn và báo lỗi chữ ký không hợp lệ. |

### 3.4. Vòng đời Chuỗi Hub, Hủy đơn & Hoàn hàng (`TC-LIFE`)
| Mã TC | Kịch bản kiểm thử | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| **TC-LIFE-01** | Chạy trọn luồng **Liên tỉnh** (`HCM-TD01 → HN-CG01`) | `CREATED → ASSIGNED_PICKUP → PICKED_UP → IN_ORIGIN_HUB → IN_TRANSIT → IN_DEST_HUB → OUT_FOR_DELIVERY → DELIVERED`; mỗi bước sinh 1 tracking log đúng bưu cục và người thao tác. |
| **TC-LIFE-02** | Chạy luồng **Nội vùng** (`HCM-TD01 → HCM-TD01`) | Từ `IN_ORIGIN_HUB` chuyển thẳng `IN_DEST_HUB` (không qua `IN_TRANSIT`). |
| **TC-LIFE-03** | Staff Hà Nội cố thao tác đơn đang ở `HCM-TD01` (đơn nội vùng TP.HCM) | Bị từ chối `403` do khác phạm vi bưu cục. |
| **TC-LIFE-04** | Customer hủy đơn đang ở `CREATED` (đã thanh toán VNPay) | Đơn chuyển `CANCELLED`, `paymentStatus = REFUND_PENDING`. |
| **TC-LIFE-05** | Customer gửi request hủy đơn khi đơn đã ở `PICKED_UP` | Hệ thống chặn, báo lỗi nghiệp vụ *"Không thể hủy đơn sau khi đã lấy hàng"*. |
| **TC-LIFE-06** | Shipper báo giao thất bại 3 lần liên tiếp (`failed_delivery_count = 3`) | Sau lần 3, nút *"Sắp xếp giao lại"* bị vô hiệu hóa; Staff chỉ có thể kích hoạt `RETURNING → RETURNED`. |

### 3.5. Upload Ảnh POD (Cloudflare R2) & Tem PDF A6/A5 (`TC-MEDIA`)
| Mã TC | Kịch bản kiểm thử | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| **TC-MEDIA-01** | Shipper bấm *"Giao thành công"* nhưng không đính kèm ảnh POD | Chặn submit, báo lỗi bắt buộc ảnh bằng chứng giao hàng. |
| **TC-MEDIA-02** | Shipper upload file `.pdf` hoặc ảnh `> 5MB` | Chặn và trả thông báo lỗi định dạng/dung lượng hợp lệ (`JPG/PNG/WEBP <= 5MB`). |
| **TC-MEDIA-03** | Shipper upload ảnh `.jpg` hợp lệ | Ảnh lưu thành công trên **Cloudflare R2** với key `pod/{uuid}.jpg`; Customer/Staff mở chi tiết đơn xem và phóng to ảnh rõ nét qua `/files/pod/{shipmentId}`. |
| **TC-MEDIA-04** | Tải PDF tem vận đơn khổ `A6` và `?size=A5` cho đơn có dấu tiếng Việt + cờ *Hàng dễ vỡ* | File PDF mở được, không lỗi font dấu tiếng Việt, có đủ Barcode Code128, QR Code, Routing Code, nhãn `[HÀNG DỄ VỠ]`, chỉ dẫn đồng kiểm và đúng tổng tiền thu người nhận. |

### 3.6. Realtime WebSocket & Tra cứu Đa Vận đơn (`TC-WS`)
| Mã TC | Kịch bản kiểm thử | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| **TC-WS-01** | Cửa sổ 1 (Guest) mở `/tracking?codes=UTE...`; Cửa sổ 2 (Staff/Shipper) cập nhật trạng thái đơn | Cửa sổ 1 tự động cập nhật badge trạng thái và chèn mốc timeline mới trong `< 2 giây` không cần F5; SĐT người gửi/nhận vẫn bị che. |
| **TC-WS-02** | Tra cứu 3 mã vận đơn cùng lúc (2 mã đúng + 1 mã sai) | Hiển thị đủ 2 đơn hợp lệ (chọn xem từng đơn) và báo rõ mã không tìm thấy; nếu nhập > 5 mã thì báo giới hạn tối đa 5 mã/lần. |
| **TC-WS-03** | Customer và Staff mở chi tiết cùng 1 vận đơn và nhắn tin qua Chat CSKH | Tin nhắn hiện tức thì ở 2 phía; tải lại trang vẫn còn đủ lịch sử từ bảng `chat_messages`. |
| **TC-WS-04** | Customer B cố kết nối/đọc lịch sử chat đơn của Customer A | Bị từ chối (`403` cho API lịch sử và chặn tại `ChannelInterceptor` của WebSocket). |

### 3.7. Quản lý & Đối soát COD (`TC-COD`)
| Mã TC | Kịch bản kiểm thử | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| **TC-COD-01** | Giao thành công 2 đơn của cùng 1 Shop: Đơn 1 (`COD 300.000`, đã trả VNPay), Đơn 2 (`COD 200.000`, Người gửi trả tiền mặt cước `30.000` chưa thanh toán) | Cả 2 đơn chuyển `HELD_BY_SHIPPER` → Staff thu COD (`COLLECTED_BY_HUB`) → Admin lập phiếu đối soát: `totalCod = 500.000`, `deduction = 30.000`, `netAmount = 470.000 đ`, hiển thị đúng thông tin ngân hàng của Shop. |
| **TC-COD-02** | Admin xác nhận chuyển khoản (`SETTLED`) kèm `transactionRef` | Phiếu đối soát chuyển `SETTLED`, 2 vận đơn đổi `codStatus = SETTLED`, Shop nhận thông báo WebSocket ngay lập tức và không thể lập phiếu đối soát trùng cho 2 đơn này nữa. |

---

## 4. Checklist Kiểm tra trước khi Demo / Nộp bài

- [ ] `docker compose up -d` khởi chạy PostgreSQL 16 healthy trên port `5432`.
- [ ] `mvn test` toàn bộ Unit Tests xanh 100%.
- [ ] `mvn spring-boot:run` khởi động sạch không lỗi Flyway, nạp đủ dữ liệu mẫu `V2__seed_data.sql`.
- [ ] Kết nối **Cloudflare R2** upload & xem ảnh POD hoạt động bình thường.
- [ ] Thanh toán **VNPay Sandbox** chạy thông suốt với thẻ test NCB (`9704198526191432198`).
- [ ] Kiểm tra 2 trình duyệt chạy song song cho **Live Tracking**, **Thông báo chuông** và **Chat CSKH WebSocket**.
- [ ] File `.env` đã nằm trong `.gitignore`, tuyệt đối không lộ `JWT_SECRET`, `VNPAY_HASH_SECRET`, `R2_SECRET_ACCESS_KEY` lên Git.
