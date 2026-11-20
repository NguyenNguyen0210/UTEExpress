package com.uteexpress.repository;

import com.uteexpress.entity.Shipment;
import com.uteexpress.enums.CodStatus;
import com.uteexpress.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {

    Optional<Shipment> findByTrackingCodeIgnoreCase(String trackingCode);

    Optional<Shipment> findByVnpTxnRef(String vnpTxnRef);

    boolean existsByTrackingCodeIgnoreCase(String trackingCode);

    List<Shipment> findByTrackingCodeIn(Collection<String> trackingCodes);

    Page<Shipment> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Shipment> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, ShipmentStatus status, Pageable pageable);

    long countByCustomerId(Long customerId);

    long countByCustomerIdAndStatus(Long customerId, ShipmentStatus status);

    long countByCustomerIdAndStatusIn(Long customerId, Collection<ShipmentStatus> statuses);

    List<Shipment> findTop10ByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Shipment> findByPickupShipperIdAndStatusOrderByCreatedAtDesc(Long shipperId, ShipmentStatus status);

    List<Shipment> findByDeliveryShipperIdAndStatusOrderByCreatedAtDesc(Long shipperId, ShipmentStatus status);

    List<Shipment> findByDeliveryShipperIdAndCodStatusOrderByDeliveredAtDesc(Long shipperId, CodStatus codStatus);

    @Query("""
            SELECT s FROM Shipment s
            WHERE (s.pickupShipper.id = :shipperId OR s.deliveryShipper.id = :shipperId)
              AND s.status IN :statuses
            ORDER BY s.updatedAt DESC
            """)
    List<Shipment> findShipperCompletedTasks(
            @Param("shipperId") Long shipperId,
            @Param("statuses") Collection<ShipmentStatus> statuses
    );

    @Query("""
            SELECT s FROM Shipment s
            WHERE s.originHub.id = :hubId OR s.destHub.id = :hubId OR s.currentHub.id = :hubId
            ORDER BY s.createdAt DESC
            """)
    Page<Shipment> findByHubScope(@Param("hubId") Long hubId, Pageable pageable);

    List<Shipment> findByDestHubIdAndCodStatusOrderByDeliveredAtDesc(Long destHubId, CodStatus codStatus);

    List<Shipment> findByCustomerIdAndStatusAndCodStatusAndSettlementIsNullOrderByDeliveredAtAsc(
            Long customerId,
            ShipmentStatus status,
            CodStatus codStatus
    );

    List<Shipment> findByStatusAndCodStatusAndSettlementIsNullOrderByDeliveredAtAsc(
            ShipmentStatus status,
            CodStatus codStatus
    );

    List<Shipment> findBySettlementIdOrderByDeliveredAtAsc(Long settlementId);

    List<Shipment> findByCreatedAtBetweenOrderByCreatedAtAsc(OffsetDateTime from, OffsetDateTime to);
}
