package com.uteexpress.entity;

import com.uteexpress.enums.HubType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "hubs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private HubType type;

    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

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
