package com.uteexpress.repository;

import com.uteexpress.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

    Optional<ServiceType> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<ServiceType> findByActiveTrueOrderByBaseFeeAsc();

    List<ServiceType> findAllByOrderByIdAsc();
}
