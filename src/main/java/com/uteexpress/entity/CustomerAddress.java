package com.uteexpress.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "address_detail", nullable = false, length = 255)
    private String addressDetail;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.isDefault == null) {
            this.isDefault = false;
        }
    }
}
