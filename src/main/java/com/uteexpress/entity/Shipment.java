package com.uteexpress.entity;

import com.uteexpress.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_code", nullable = false, unique = true, length = 30)
    private String trackingCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Column(name = "sender_name", nullable = false, length = 120)
    private String senderName;

    @Column(name = "sender_phone", nullable = false, length = 20)
    private String senderPhone;

    @Column(name = "sender_province", nullable = false, length = 100)
    private String senderProvince;

    @Column(name = "sender_district", nullable = false, length = 100)
    private String senderDistrict;

    @Column(name = "sender_address", nullable = false, length = 255)
    private String senderAddress;

    @Column(name = "receiver_name", nullable = false, length = 120)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "receiver_province", nullable = false, length = 100)
    private String receiverProvince;

    @Column(name = "receiver_district", nullable = false, length = 100)
    private String receiverDistrict;

    @Column(name = "receiver_address", nullable = false, length = 255)
    private String receiverAddress;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "origin_hub_id", nullable = false)
    private Hub originHub;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dest_hub_id", nullable = false)
    private Hub destHub;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_hub_id", nullable = false)
    private Hub currentHub;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pickup_shipper_id")
    private User pickupShipper;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "delivery_shipper_id")
    private User deliveryShipper;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "actual_weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal actualWeightKg;

    @Column(name = "length_cm")
    private Integer lengthCm;

    @Column(name = "width_cm")
    private Integer widthCm;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "chargeable_weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal chargeableWeightKg;

    @Builder.Default
    @Column(name = "is_fragile", nullable = false)
    private Boolean isFragile = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_policy", nullable = false, length = 30)
    private InspectionPolicy inspectionPolicy = InspectionPolicy.ALLOW_VIEW_NO_TRY;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 0)
    private BigDecimal shippingFee;

    @Builder.Default
    @Column(name = "cod_fee", nullable = false, precision = 15, scale = 0)
    private BigDecimal codFee = BigDecimal.ZERO;

    @Column(name = "total_fee", nullable = false, precision = 15, scale = 0)
    private BigDecimal totalFee;

    @Builder.Default
    @Column(name = "cod_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal codAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 20)
    private PayerType payerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "vnp_txn_ref", unique = true, length = 64)
    private String vnpTxnRef;

    @Column(name = "vnp_transaction_no", length = 64)
    private String vnpTransactionNo;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ShipmentStatus status;

    @Builder.Default
    @Column(name = "failed_delivery_count", nullable = false)
    private Integer failedDeliveryCount = 0;

    @Column(name = "pod_image_key", length = 255)
    private String podImageKey;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "cod_status", nullable = false, length = 25)
    private CodStatus codStatus = CodStatus.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cod_collected_by")
    private User codCollectedBy;

    @Column(name = "cod_collected_at")
    private OffsetDateTime codCollectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private CodSettlement settlement;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.isFragile == null) {
            this.isFragile = false;
        }
        if (this.inspectionPolicy == null) {
            this.inspectionPolicy = InspectionPolicy.ALLOW_VIEW_NO_TRY;
        }
        if (this.failedDeliveryCount == null) {
            this.failedDeliveryCount = 0;
        }
        if (this.paymentStatus == null) {
            this.paymentStatus = PaymentStatus.UNPAID;
        }
        if (this.codStatus == null) {
            this.codStatus = CodStatus.NONE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
