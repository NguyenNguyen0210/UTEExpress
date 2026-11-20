package com.uteexpress.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "service_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "base_fee", nullable = false, precision = 15, scale = 0)
    private BigDecimal baseFee;

    @Builder.Default
    @Column(name = "base_weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal baseWeightKg = new BigDecimal("1.00");

    @Column(name = "extra_fee_per_half_kg", nullable = false, precision = 15, scale = 0)
    private BigDecimal extraFeePerHalfKg;

    @Builder.Default
    @Column(name = "inter_province_surcharge", nullable = false, precision = 15, scale = 0)
    private BigDecimal interProvinceSurcharge = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "cod_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal codFeePercent = BigDecimal.ZERO;

    @Column(name = "estimated_days_min", nullable = false)
    private Integer estimatedDaysMin;

    @Column(name = "estimated_days_max", nullable = false)
    private Integer estimatedDaysMax;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

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
        if (this.active == null) {
            this.active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
