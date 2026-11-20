package com.uteexpress.entity;

import com.uteexpress.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "shipment_tracking_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentTrackingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ShipmentStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hub_id")
    private Hub hub;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
