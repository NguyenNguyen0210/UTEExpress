-- ============================================================
-- V2__seed_data.sql
-- UTEExpress — Initial Hubs, Service Types, Demo Accounts & Sample Shipments
-- Default password for all demo accounts: 123456
-- ============================================================

-- 1. Seed 6 Hubs (Chuỗi Bưu cục & Kho trung chuyển)
INSERT INTO hubs (code, name, type, province, district, address, phone, active) VALUES
('HCM-TD01', 'Bưu cục Thủ Đức',            'POST_OFFICE', 'TP. Hồ Chí Minh', 'TP. Thủ Đức',   '01 Võ Văn Ngân, Phường Linh Chiểu',       '02838968641', TRUE),
('HCM-Q101', 'Kho Trung chuyển Miền Nam',  'SORTING_HUB', 'TP. Hồ Chí Minh', 'Quận 1',        '120 Nguyễn Thị Minh Khai, Phường Đa Kao', '02838291122', TRUE),
('BD-TDM01', 'Bưu cục Thủ Dầu Một',        'POST_OFFICE', 'Bình Dương',      'TP. Thủ Dầu Một','456 Đại lộ Bình Dương, Phường Hiệp Thành','02743822999', TRUE),
('DN-BH01',  'Bưu cục Biên Hòa',           'POST_OFFICE', 'Đồng Nai',        'TP. Biên Hòa',  '88 Phạm Văn Thuận, Phường Tam Hiệp',      '02513812345', TRUE),
('DNG-HC01', 'Kho Trung chuyển Miền Trung','SORTING_HUB', 'Đà Nẵng',         'Quận Hải Châu', '255 Nguyễn Văn Linh, Phường Vĩnh Trung',  '02363654321', TRUE),
('HN-CG01',  'Bưu cục Cầu Giấy',           'POST_OFFICE', 'Hà Nội',          'Quận Cầu Giấy', '144 Xuân Thủy, Phường Dịch Vọng Hậu',     '02437547890', TRUE);

-- 2. Seed 3 Service Types (Gói dịch vụ & Bảng giá cước)
INSERT INTO service_types (code, name, description, base_fee, base_weight_kg, extra_fee_per_half_kg, inter_province_surcharge, cod_fee_percent, estimated_days_min, estimated_days_max, active) VALUES
('STANDARD', 'Tiêu chuẩn', 'Giao hàng tiết kiệm toàn quốc qua mạng lưới bưu cục UTEExpress', 20000, 1.00, 5000,  10000, 1.00, 3, 5, TRUE),
('EXPRESS',  'Nhanh',      'Giao hàng ưu tiên liên tỉnh & nội thành nhanh chóng',            30000, 1.00, 7000,  15000, 1.00, 1, 2, TRUE),
('SAMEDAY',  'Hỏa tốc',    'Giao nhanh ưu tiên đặc biệt trong ngày hoặc sáng hôm sau',       50000, 1.00, 10000, 30000, 1.50, 0, 1, TRUE);

-- 3. Seed 6 Demo Users (Password: 123456 -> BCrypt)
INSERT INTO users (email, phone, password_hash, full_name, role, status, hub_id, bank_name, bank_account_number, bank_account_holder) VALUES
('admin@uteexpress.vn',       '0901000001', '$2a$10$R9R4lLPph21WyBN/yGT8F.aC1QwYUTWpnVF1oMVD11vjEtgcqInpu', 'Quốc Bảo (Admin UTEExpress)',    'ADMIN',    'ACTIVE', NULL, NULL, NULL, NULL),
('staff.hcm@uteexpress.vn',   '0901000002', '$2a$10$R9R4lLPph21WyBN/yGT8F.aC1QwYUTWpnVF1oMVD11vjEtgcqInpu', 'Thu Hà (Staff Thủ Đức)',         'STAFF',    'ACTIVE', (SELECT id FROM hubs WHERE code = 'HCM-TD01'), NULL, NULL, NULL),
('staff.hn@uteexpress.vn',    '0901000003', '$2a$10$R9R4lLPph21WyBN/yGT8F.aC1QwYUTWpnVF1oMVD11vjEtgcqInpu', 'Minh Tuấn (Staff Cầu Giấy)',     'STAFF',    'ACTIVE', (SELECT id FROM hubs WHERE code = 'HN-CG01'),  NULL, NULL, NULL),
('shipper.hcm@uteexpress.vn', '0901000004', '$2a$10$R9R4lLPph21WyBN/yGT8F.aC1QwYUTWpnVF1oMVD11vjEtgcqInpu', 'Văn Hùng (Shipper Thủ Đức)',     'SHIPPER',  'ACTIVE', (SELECT id FROM hubs WHERE code = 'HCM-TD01'), NULL, NULL, NULL),
('shipper.hn@uteexpress.vn',  '0901000005', '$2a$10$R9R4lLPph21WyBN/yGT8F.aC1QwYUTWpnVF1oMVD11vjEtgcqInpu', 'Đức Anh (Shipper Cầu Giấy)',     'SHIPPER',  'ACTIVE', (SELECT id FROM hubs WHERE code = 'HN-CG01'),  NULL, NULL, NULL),
('shop@uteexpress.vn',        '0901234567', '$2a$10$R9R4lLPph21WyBN/yGT8F.aC1QwYUTWpnVF1oMVD11vjEtgcqInpu', 'Shop Hoàng Nam Tech',            'CUSTOMER', 'ACTIVE', NULL, 'Vietcombank', '1023456789', 'HOANG VAN NAM');

-- 4. Seed Customer Address Book for shop@uteexpress.vn
INSERT INTO customer_addresses (customer_id, label, contact_name, contact_phone, province, district, address_detail, is_default) VALUES
((SELECT id FROM users WHERE email = 'shop@uteexpress.vn'), 'Kho Chính Thủ Đức', 'Shop Hoàng Nam Tech', '0901234567', 'TP. Hồ Chí Minh', 'TP. Thủ Đức', '01 Võ Văn Ngân, Phường Linh Chiểu', TRUE),
((SELECT id FROM users WHERE email = 'shop@uteexpress.vn'), 'Showroom Quận 1',   'Hoàng Nam Chi Nhánh', '0901234588', 'TP. Hồ Chí Minh', 'Quận 1',      '68 Nguyễn Huệ, Phường Bến Nghé',    FALSE);

-- 5. Seed Sample Shipments across lifecycle stages
-- Shipment 1: CREATED (Mới tạo tại Bưu cục Thủ Đức, chờ phân công lấy hàng)
INSERT INTO shipments (
    tracking_code, customer_id, service_type_id,
    sender_name, sender_phone, sender_province, sender_district, sender_address,
    receiver_name, receiver_phone, receiver_province, receiver_district, receiver_address,
    origin_hub_id, dest_hub_id, current_hub_id, pickup_shipper_id, delivery_shipper_id,
    item_name, actual_weight_kg, length_cm, width_cm, height_cm, chargeable_weight_kg,
    is_fragile, inspection_policy, note,
    shipping_fee, cod_fee, total_fee, cod_amount,
    payer_type, payment_method, payment_status, status, cod_status, created_at, updated_at
) VALUES (
    'UTE261120A8K4P2',
    (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
    (SELECT id FROM service_types WHERE code = 'EXPRESS'),
    'Shop Hoàng Nam Tech', '0901234567', 'TP. Hồ Chí Minh', 'TP. Thủ Đức', '01 Võ Văn Ngân, Phường Linh Chiểu',
    'Trần Minh Anh', '0987654321', 'Hà Nội', 'Quận Cầu Giấy', '123 Xuân Thủy, Phường Dịch Vọng Hậu',
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM hubs WHERE code = 'HN-CG01'),
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    NULL, NULL,
    'Tai nghe Bluetooth Sony WH-1000XM5', 1.20, 30, 20, 15, 2.00,
    TRUE, 'ALLOW_VIEW_NO_TRY', 'Gọi trước khi giao 15 phút, hàng điện tử dễ vỡ',
    59000, 5000, 64000, 500000,
    'SENDER', 'VNPAY', 'PAID', 'CREATED', 'PENDING',
    NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'
);

-- Shipment 2: IN_TRANSIT (Đang trung chuyển liên tỉnh từ HCM-TD01 ra HN-CG01)
INSERT INTO shipments (
    tracking_code, customer_id, service_type_id,
    sender_name, sender_phone, sender_province, sender_district, sender_address,
    receiver_name, receiver_phone, receiver_province, receiver_district, receiver_address,
    origin_hub_id, dest_hub_id, current_hub_id, pickup_shipper_id, delivery_shipper_id,
    item_name, actual_weight_kg, length_cm, width_cm, height_cm, chargeable_weight_kg,
    is_fragile, inspection_policy, note,
    shipping_fee, cod_fee, total_fee, cod_amount,
    payer_type, payment_method, payment_status, status, cod_status, created_at, updated_at
) VALUES (
    'UTE261119B7M2Q9',
    (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
    (SELECT id FROM service_types WHERE code = 'STANDARD'),
    'Shop Hoàng Nam Tech', '0901234567', 'TP. Hồ Chí Minh', 'TP. Thủ Đức', '01 Võ Văn Ngân, Phường Linh Chiểu',
    'Lê Hoàng Long', '0912345678', 'Hà Nội', 'Quận Cầu Giấy', '88 Cầu Giấy, Phường Quan Hoa',
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM hubs WHERE code = 'HN-CG01'),
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM users WHERE email = 'shipper.hcm@uteexpress.vn'),
    NULL,
    'Bàn phím cơ Keychron K8 Pro', 1.50, 40, 18, 8, 1.50,
    TRUE, 'ALLOW_VIEW_NO_TRY', 'Giao giờ hành chính',
    35000, 8000, 43000, 800000,
    'RECEIVER', 'CASH', 'UNPAID', 'IN_TRANSIT', 'PENDING',
    NOW() - INTERVAL '1 day', NOW() - INTERVAL '6 hours'
);

-- Shipment 3: OUT_FOR_DELIVERY (Đơn nội vùng TP.HCM đang trên tay Shipper Thủ Đức đi giao)
INSERT INTO shipments (
    tracking_code, customer_id, service_type_id,
    sender_name, sender_phone, sender_province, sender_district, sender_address,
    receiver_name, receiver_phone, receiver_province, receiver_district, receiver_address,
    origin_hub_id, dest_hub_id, current_hub_id, pickup_shipper_id, delivery_shipper_id,
    item_name, actual_weight_kg, length_cm, width_cm, height_cm, chargeable_weight_kg,
    is_fragile, inspection_policy, note,
    shipping_fee, cod_fee, total_fee, cod_amount,
    payer_type, payment_method, payment_status, status, cod_status, created_at, updated_at
) VALUES (
    'UTE261119C3N5X1',
    (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
    (SELECT id FROM service_types WHERE code = 'SAMEDAY'),
    'Shop Hoàng Nam Tech', '0901234567', 'TP. Hồ Chí Minh', 'TP. Thủ Đức', '01 Võ Văn Ngân, Phường Linh Chiểu',
    'Phạm Thu Trang', '0938112233', 'TP. Hồ Chí Minh', 'TP. Thủ Đức', '240 Kha Vạn Cân, Phường Hiệp Bình Chánh',
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM users WHERE email = 'shipper.hcm@uteexpress.vn'),
    (SELECT id FROM users WHERE email = 'shipper.hcm@uteexpress.vn'),
    'Chuột không dây Logitech MX Master 3S', 0.80, NULL, NULL, NULL, 1.00,
    FALSE, 'ALLOW_TRY', 'Cho khách kiểm tra ngoại quan',
    50000, 4500, 54500, 300000,
    'SENDER', 'CASH', 'UNPAID', 'OUT_FOR_DELIVERY', 'PENDING',
    NOW() - INTERVAL '18 hours', NOW() - INTERVAL '2 hours'
);

-- Shipment 4: DELIVERED (Đã giao thành công tại Hà Nội, Shipper đang giữ tiền COD = HELD_BY_SHIPPER)
INSERT INTO shipments (
    tracking_code, customer_id, service_type_id,
    sender_name, sender_phone, sender_province, sender_district, sender_address,
    receiver_name, receiver_phone, receiver_province, receiver_district, receiver_address,
    origin_hub_id, dest_hub_id, current_hub_id, pickup_shipper_id, delivery_shipper_id,
    item_name, actual_weight_kg, length_cm, width_cm, height_cm, chargeable_weight_kg,
    is_fragile, inspection_policy, note,
    shipping_fee, cod_fee, total_fee, cod_amount,
    payer_type, payment_method, payment_status, status, cod_status, delivered_at, created_at, updated_at
) VALUES (
    'UTE261118D9P4W7',
    (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
    (SELECT id FROM service_types WHERE code = 'EXPRESS'),
    'Shop Hoàng Nam Tech', '0901234567', 'TP. Hồ Chí Minh', 'TP. Thủ Đức', '01 Võ Văn Ngân, Phường Linh Chiểu',
    'Nguyễn Hải Đăng', '0977889900', 'Hà Nội', 'Quận Cầu Giấy', '55 Trần Đăng Ninh, Phường Dịch Vọng',
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM hubs WHERE code = 'HN-CG01'),
    (SELECT id FROM hubs WHERE code = 'HN-CG01'),
    (SELECT id FROM users WHERE email = 'shipper.hcm@uteexpress.vn'),
    (SELECT id FROM users WHERE email = 'shipper.hn@uteexpress.vn'),
    'Ổ cứng SSD Samsung 990 Pro 1TB', 0.50, NULL, NULL, NULL, 1.00,
    TRUE, 'ALLOW_VIEW_NO_TRY', 'Giao tận tay người nhận',
    45000, 6000, 51000, 600000,
    'SENDER', 'VNPAY', 'PAID', 'DELIVERED', 'HELD_BY_SHIPPER',
    NOW() - INTERVAL '1 day', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day'
);

-- Shipment 5: DELIVERED & COLLECTED_BY_HUB (Sẵn sàng để Admin tạo Phiếu đối soát COD)
INSERT INTO shipments (
    tracking_code, customer_id, service_type_id,
    sender_name, sender_phone, sender_province, sender_district, sender_address,
    receiver_name, receiver_phone, receiver_province, receiver_district, receiver_address,
    origin_hub_id, dest_hub_id, current_hub_id, pickup_shipper_id, delivery_shipper_id,
    item_name, actual_weight_kg, length_cm, width_cm, height_cm, chargeable_weight_kg,
    is_fragile, inspection_policy, note,
    shipping_fee, cod_fee, total_fee, cod_amount,
    payer_type, payment_method, payment_status, status, cod_status,
    cod_collected_by, cod_collected_at, delivered_at, created_at, updated_at
) VALUES (
    'UTE261117E5R8K3',
    (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
    (SELECT id FROM service_types WHERE code = 'STANDARD'),
    'Shop Hoàng Nam Tech', '0901234567', 'TP. Hồ Chí Minh', 'TP. Thủ Đức', '01 Võ Văn Ngân, Phường Linh Chiểu',
    'Vũ Ngọc Mai', '0966554433', 'TP. Hồ Chí Minh', 'TP. Thủ Đức', '15 Đặng Văn Bi, Phường Bình Thọ',
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
    (SELECT id FROM users WHERE email = 'shipper.hcm@uteexpress.vn'),
    (SELECT id FROM users WHERE email = 'shipper.hcm@uteexpress.vn'),
    'Sạc nhanh Anker GaNPrime 65W', 0.40, NULL, NULL, NULL, 1.00,
    FALSE, 'ALLOW_VIEW_NO_TRY', NULL,
    20000, 4000, 24000, 400000,
    'SENDER', 'CASH', 'UNPAID', 'DELIVERED', 'COLLECTED_BY_HUB',
    (SELECT id FROM users WHERE email = 'staff.hcm@uteexpress.vn'),
    NOW() - INTERVAL '12 hours', NOW() - INTERVAL '1 day', NOW() - INTERVAL '3 days', NOW() - INTERVAL '12 hours'
);

-- 6. Seed Tracking Logs for Sample Shipments
INSERT INTO shipment_tracking_logs (shipment_id, status, hub_id, actor_id, title, description, created_at) VALUES
-- Logs for Shipment 1 (UTE261120A8K4P2)
((SELECT id FROM shipments WHERE tracking_code = 'UTE261120A8K4P2'), 'CREATED',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
 'Đã tạo vận đơn & thanh toán VNPay thành công',
 'Vận đơn được khởi tạo tại Bưu cục Thủ Đức (HCM-TD01), chờ điều phối viên phân công lấy hàng.',
 NOW() - INTERVAL '3 hours'),

-- Logs for Shipment 2 (UTE261119B7M2Q9)
((SELECT id FROM shipments WHERE tracking_code = 'UTE261119B7M2Q9'), 'CREATED',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
 'Khởi tạo vận đơn mới',
 'Shop Hoàng Nam Tech đã tạo đơn gửi đi Hà Nội.',
 NOW() - INTERVAL '1 day'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261119B7M2Q9'), 'ASSIGNED_PICKUP',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'staff.hcm@uteexpress.vn'),
 'Đã phân công Shipper lấy hàng',
 'Bưu cục Thủ Đức đã gán Shipper Văn Hùng đến lấy hàng.',
 NOW() - INTERVAL '20 hours'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261119B7M2Q9'), 'PICKED_UP',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'shipper.hcm@uteexpress.vn'),
 'Shipper đã lấy hàng thành công',
 'Shipper Văn Hùng đã nhận kiện hàng từ người gửi.',
 NOW() - INTERVAL '16 hours'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261119B7M2Q9'), 'IN_ORIGIN_HUB',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'staff.hcm@uteexpress.vn'),
 'Đã nhập kho Bưu cục Thủ Đức (HCM-TD01)',
 'Kiện hàng đã được kiểm đếm và nhập kho bưu cục gửi.',
 NOW() - INTERVAL '12 hours'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261119B7M2Q9'), 'IN_TRANSIT',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'staff.hcm@uteexpress.vn'),
 'Đã xuất kho trung chuyển liên tỉnh',
 'Đang luân chuyển trên tuyến TP. Hồ Chí Minh (HCM-TD01) -> Hà Nội (HN-CG01).',
 NOW() - INTERVAL '6 hours'),

-- Logs for Shipment 3 (UTE261119C3N5X1)
((SELECT id FROM shipments WHERE tracking_code = 'UTE261119C3N5X1'), 'CREATED',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
 'Khởi tạo vận đơn Hỏa tốc nội vùng',
 'Đơn hàng nội vùng TP. Thủ Đức.',
 NOW() - INTERVAL '18 hours'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261119C3N5X1'), 'PICKED_UP',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'shipper.hcm@uteexpress.vn'),
 'Đã lấy hàng từ Shop Hoàng Nam Tech',
 'Shipper Văn Hùng đã lấy kiện hàng.',
 NOW() - INTERVAL '10 hours'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261119C3N5X1'), 'IN_DEST_HUB',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'staff.hcm@uteexpress.vn'),
 'Hàng sẵn sàng phát tại Bưu cục Thủ Đức',
 'Đơn nội vùng chuyển thẳng sang khâu phân công giao hàng.',
 NOW() - INTERVAL '5 hours'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261119C3N5X1'), 'OUT_FOR_DELIVERY',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'staff.hcm@uteexpress.vn'),
 'Shipper đang trên đường giao hàng',
 'Shipper Văn Hùng (0901000004) đang giao kiện hàng tới người nhận.',
 NOW() - INTERVAL '2 hours'),

-- Logs for Shipment 4 (UTE261118D9P4W7)
((SELECT id FROM shipments WHERE tracking_code = 'UTE261118D9P4W7'), 'CREATED',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
 'Đã tạo vận đơn & thanh toán cước trực tuyến',
 'Vận đơn Express từ TP.HCM đi Hà Nội.',
 NOW() - INTERVAL '2 days'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261118D9P4W7'), 'IN_DEST_HUB',
 (SELECT id FROM hubs WHERE code = 'HN-CG01'),
 (SELECT id FROM users WHERE email = 'staff.hn@uteexpress.vn'),
 'Đã nhập kho Bưu cục Cầu Giấy (HN-CG01)',
 'Kiện hàng đã đến bưu cục nhận tại Hà Nội.',
 NOW() - INTERVAL '30 hours'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261118D9P4W7'), 'DELIVERED',
 (SELECT id FROM hubs WHERE code = 'HN-CG01'),
 (SELECT id FROM users WHERE email = 'shipper.hn@uteexpress.vn'),
 'Giao hàng thành công & đã thu hộ COD 600.000 đ',
 'Người nhận Nguyễn Hải Đăng đã nhận hàng và ký xác nhận POD.',
 NOW() - INTERVAL '1 day'),

-- Logs for Shipment 5 (UTE261117E5R8K3)
((SELECT id FROM shipments WHERE tracking_code = 'UTE261117E5R8K3'), 'CREATED',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
 'Đã tạo vận đơn nội vùng',
 'Đơn Tiêu chuẩn tại TP. Thủ Đức.',
 NOW() - INTERVAL '3 days'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261117E5R8K3'), 'DELIVERED',
 (SELECT id FROM hubs WHERE code = 'HCM-TD01'),
 (SELECT id FROM users WHERE email = 'shipper.hcm@uteexpress.vn'),
 'Giao hàng thành công & thu COD 400.000 đ',
 'Shipper đã nộp tiền COD về Bưu cục Thủ Đức, sẵn sàng đối soát cho Shop.',
 NOW() - INTERVAL '1 day');

-- 7. Seed Sample Notifications & Chat Messages
INSERT INTO notifications (user_id, title, message, link_url, is_read) VALUES
((SELECT id FROM users WHERE email = 'staff.hcm@uteexpress.vn'),
 'Vận đơn mới cần điều phối',
 'Vận đơn UTE261120A8K4P2 vừa được tạo tại Bưu cục Thủ Đức, đang chờ phân công Shipper lấy hàng.',
 '/staff/shipments/1', FALSE),
((SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
 'Đơn hàng UTE261118D9P4W7 đã giao thành công',
 'Kiện hàng gửi cho Nguyễn Hải Đăng tại Hà Nội đã được giao thành công.',
 '/customer/shipments/4', FALSE);

INSERT INTO chat_messages (shipment_id, sender_id, content, created_at) VALUES
((SELECT id FROM shipments WHERE tracking_code = 'UTE261120A8K4P2'),
 (SELECT id FROM users WHERE email = 'shop@uteexpress.vn'),
 'Nhờ Bưu cục Thủ Đức cho shipper qua lấy đơn này trước 16h chiều nay giúp shop nhé!',
 NOW() - INTERVAL '2 hours'),
((SELECT id FROM shipments WHERE tracking_code = 'UTE261120A8K4P2'),
 (SELECT id FROM users WHERE email = 'staff.hcm@uteexpress.vn'),
 'Dạ Bưu cục Thủ Đức đã tiếp nhận thông tin, bên mình sẽ điều phối shipper qua lấy ngay trong chiều nay ạ.',
 NOW() - INTERVAL '1 hour');
