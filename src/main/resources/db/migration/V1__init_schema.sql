-- ============================================================
-- V1__init_schema.sql
-- UTEExpress — Multi-Hub Logistics Chain Schema (9 Core Tables)
-- ============================================================

-- 1. hubs — Chuỗi bưu cục & kho trung chuyển
CREATE TABLE hubs (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    province    VARCHAR(100) NOT NULL,
    district    VARCHAR(100) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 2. users — Người dùng hệ thống (CUSTOMER, STAFF, SHIPPER, ADMIN)
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(150) NOT NULL UNIQUE,
    phone               VARCHAR(20)  NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(120) NOT NULL,
    role                VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    hub_id              BIGINT       NULL REFERENCES hubs(id),
    bank_name           VARCHAR(100) NULL,
    bank_account_number VARCHAR(50)  NULL,
    bank_account_holder VARCHAR(120) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 3. service_types — Gói dịch vụ & Bảng giá cước
CREATE TABLE service_types (
    id                       BIGSERIAL PRIMARY KEY,
    code                     VARCHAR(30)   NOT NULL UNIQUE,
    name                     VARCHAR(100)  NOT NULL,
    description              VARCHAR(255)  NULL,
    base_fee                 NUMERIC(15,0) NOT NULL,
    base_weight_kg           NUMERIC(6,2)  NOT NULL DEFAULT 1.00,
    extra_fee_per_half_kg    NUMERIC(15,0) NOT NULL,
    inter_province_surcharge NUMERIC(15,0) NOT NULL DEFAULT 0,
    cod_fee_percent          NUMERIC(5,2)  NOT NULL DEFAULT 0.00,
    estimated_days_min       INT           NOT NULL,
    estimated_days_max       INT           NOT NULL,
    active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- 4. customer_addresses — Sổ địa chỉ của Khách hàng
CREATE TABLE customer_addresses (
    id             BIGSERIAL PRIMARY KEY,
    customer_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label          VARCHAR(80)  NOT NULL,
    contact_name   VARCHAR(120) NOT NULL,
    contact_phone  VARCHAR(20)  NOT NULL,
    province       VARCHAR(100) NOT NULL,
    district       VARCHAR(100) NOT NULL,
    address_detail VARCHAR(255) NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 5. cod_settlements — Phiếu đối soát & thanh toán COD
CREATE TABLE cod_settlements (
    id                       BIGSERIAL PRIMARY KEY,
    settlement_code          VARCHAR(30)   NOT NULL UNIQUE,
    customer_id              BIGINT        NOT NULL REFERENCES users(id),
    total_cod_amount         NUMERIC(15,0) NOT NULL,
    total_shipping_deduction NUMERIC(15,0) NOT NULL DEFAULT 0,
    net_amount               NUMERIC(15,0) NOT NULL,
    bank_name                VARCHAR(100)  NULL,
    bank_account_number      VARCHAR(50)   NULL,
    bank_account_holder      VARCHAR(120)  NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    transaction_ref          VARCHAR(100)  NULL,
    note                     TEXT          NULL,
    created_by               BIGINT        NOT NULL REFERENCES users(id),
    settled_at               TIMESTAMPTZ   NULL,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- 6. shipments — Vận đơn (Bảng trung tâm)
CREATE TABLE shipments (
    id                    BIGSERIAL PRIMARY KEY,
    tracking_code         VARCHAR(30)   NOT NULL UNIQUE,
    customer_id           BIGINT        NOT NULL REFERENCES users(id),
    service_type_id       BIGINT        NOT NULL REFERENCES service_types(id),
    sender_name           VARCHAR(120)  NOT NULL,
    sender_phone          VARCHAR(20)   NOT NULL,
    sender_province       VARCHAR(100)  NOT NULL,
    sender_district       VARCHAR(100)  NOT NULL,
    sender_address        VARCHAR(255)  NOT NULL,
    receiver_name         VARCHAR(120)  NOT NULL,
    receiver_phone        VARCHAR(20)   NOT NULL,
    receiver_province     VARCHAR(100)  NOT NULL,
    receiver_district     VARCHAR(100)  NOT NULL,
    receiver_address      VARCHAR(255)  NOT NULL,
    origin_hub_id         BIGINT        NOT NULL REFERENCES hubs(id),
    dest_hub_id           BIGINT        NOT NULL REFERENCES hubs(id),
    current_hub_id        BIGINT        NOT NULL REFERENCES hubs(id),
    pickup_shipper_id     BIGINT        NULL REFERENCES users(id),
    delivery_shipper_id   BIGINT        NULL REFERENCES users(id),
    item_name             VARCHAR(200)  NOT NULL,
    actual_weight_kg      NUMERIC(8,2)  NOT NULL,
    length_cm             INT           NULL,
    width_cm              INT           NULL,
    height_cm             INT           NULL,
    chargeable_weight_kg  NUMERIC(8,2)  NOT NULL,
    is_fragile            BOOLEAN       NOT NULL DEFAULT FALSE,
    inspection_policy     VARCHAR(30)   NOT NULL DEFAULT 'ALLOW_VIEW_NO_TRY',
    note                  TEXT          NULL,
    shipping_fee          NUMERIC(15,0) NOT NULL,
    cod_fee               NUMERIC(15,0) NOT NULL DEFAULT 0,
    total_fee             NUMERIC(15,0) NOT NULL,
    cod_amount            NUMERIC(15,0) NOT NULL DEFAULT 0,
    payer_type            VARCHAR(20)   NOT NULL,
    payment_method        VARCHAR(20)   NOT NULL,
    payment_status        VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',
    vnp_txn_ref           VARCHAR(64)   NULL UNIQUE,
    vnp_transaction_no    VARCHAR(64)   NULL,
    paid_at               TIMESTAMPTZ   NULL,
    status                VARCHAR(30)   NOT NULL,
    failed_delivery_count INT           NOT NULL DEFAULT 0,
    pod_image_key         VARCHAR(255)  NULL,
    delivered_at          TIMESTAMPTZ   NULL,
    cod_status            VARCHAR(25)   NOT NULL DEFAULT 'NONE',
    cod_collected_by      BIGINT        NULL REFERENCES users(id),
    cod_collected_at      TIMESTAMPTZ   NULL,
    settlement_id         BIGINT        NULL REFERENCES cod_settlements(id),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- 7. shipment_tracking_logs — Lịch sử hành trình vận đơn
CREATE TABLE shipment_tracking_logs (
    id          BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT       NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    status      VARCHAR(30)  NOT NULL,
    hub_id      BIGINT       NULL REFERENCES hubs(id),
    actor_id    BIGINT       NULL REFERENCES users(id),
    title       VARCHAR(150) NOT NULL,
    description TEXT         NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 8. notifications — Thông báo đẩy WebSocket
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(150) NOT NULL,
    message    TEXT         NOT NULL,
    link_url   VARCHAR(255) NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 9. chat_messages — Lịch sử chat CSKH theo vận đơn
CREATE TABLE chat_messages (
    id          BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT      NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    sender_id   BIGINT      NOT NULL REFERENCES users(id),
    content     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Essential Indexes (12 indexes as specified in 05-DATABASE-DESIGN.md)
-- ============================================================
CREATE INDEX idx_users_hub_role          ON users(hub_id, role);
CREATE INDEX idx_shipments_customer_created ON shipments(customer_id, created_at DESC);
CREATE INDEX idx_shipments_status        ON shipments(status);
CREATE INDEX idx_shipments_origin_hub    ON shipments(origin_hub_id, status);
CREATE INDEX idx_shipments_dest_hub      ON shipments(dest_hub_id, status);
CREATE INDEX idx_shipments_pickup_shipper   ON shipments(pickup_shipper_id, status);
CREATE INDEX idx_shipments_delivery_shipper ON shipments(delivery_shipper_id, status);
CREATE INDEX idx_shipments_cod_status    ON shipments(cod_status, customer_id);
CREATE INDEX idx_shipments_created_at    ON shipments(created_at);
CREATE INDEX idx_tracking_logs_shipment  ON shipment_tracking_logs(shipment_id, created_at DESC);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_chat_messages_shipment  ON chat_messages(shipment_id, created_at ASC);
