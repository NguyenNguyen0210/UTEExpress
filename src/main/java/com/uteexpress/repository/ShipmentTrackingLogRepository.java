package com.uteexpress.repository;

import com.uteexpress.entity.ShipmentTrackingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipmentTrackingLogRepository extends JpaRepository<ShipmentTrackingLog, Long> {

    List<ShipmentTrackingLog> findByShipmentIdOrderByCreatedAtDescIdDesc(Long shipmentId);
}
