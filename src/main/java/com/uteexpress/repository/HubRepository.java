package com.uteexpress.repository;

import com.uteexpress.entity.Hub;
import com.uteexpress.enums.HubType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HubRepository extends JpaRepository<Hub, Long> {

    Optional<Hub> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Hub> findByActiveTrueOrderByProvinceAscNameAsc();

    List<Hub> findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc(String province);

    List<Hub> findByTypeAndActiveTrueOrderByProvinceAscNameAsc(HubType type);

    List<Hub> findByProvinceIgnoreCaseAndTypeAndActiveTrueOrderByNameAsc(String province, HubType type);

    List<Hub> findAllByOrderByIdAsc();
}
