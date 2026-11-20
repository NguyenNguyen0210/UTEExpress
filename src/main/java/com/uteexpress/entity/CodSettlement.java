package com.uteexpress.entity;

import com.uteexpress.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cod_settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_code", nullable = false, unique = true, length = 30)
    private String settlementCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "total_cod_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal totalCodAmount;

    @Builder.Default
    @Column(name = "total_shipping_deduction", nullable = false, precision = 15, scale = 0)
    private BigDecimal totalShippingDeduction = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal netAmount;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder", length = 120)
    private String bankAccountHolder;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.status == null) {
            this.status = SettlementStatus.PENDING;
        }
    }
}
