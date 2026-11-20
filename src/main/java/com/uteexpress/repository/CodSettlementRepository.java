package com.uteexpress.repository;

import com.uteexpress.entity.CodSettlement;
import com.uteexpress.enums.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodSettlementRepository extends JpaRepository<CodSettlement, Long> {

    Optional<CodSettlement> findBySettlementCodeIgnoreCase(String settlementCode);

    List<CodSettlement> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Page<CodSettlement> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    List<CodSettlement> findAllByOrderByCreatedAtDesc();

    List<CodSettlement> findByStatusOrderByCreatedAtDesc(SettlementStatus status);
}
