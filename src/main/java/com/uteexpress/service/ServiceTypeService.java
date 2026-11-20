package com.uteexpress.service;

import com.uteexpress.dto.admin.ServiceTypeFormRequest;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceTypeService {

    private final ServiceTypeRepository serviceTypeRepository;

    public List<ServiceType> findAll() {
        return serviceTypeRepository.findAllByOrderByIdAsc();
    }

    public List<ServiceType> findActiveServices() {
        return serviceTypeRepository.findByActiveTrueOrderByBaseFeeAsc();
    }

    public ServiceType getById(Long id) {
        return serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ với ID: " + id));
    }

    @Transactional
    public ServiceType saveOrUpdate(ServiceTypeFormRequest request) {
        if (request.getEstimatedDaysMax() < request.getEstimatedDaysMin()) {
            throw new BusinessException("Số ngày giao tối đa không được nhỏ hơn số ngày tối thiểu");
        }

        String normalizedCode = request.getCode().trim().toUpperCase();
        ServiceType serviceType;

        if (request.getId() != null) {
            serviceType = getById(request.getId());
            if (!serviceType.getCode().equalsIgnoreCase(normalizedCode)
                    && serviceTypeRepository.existsByCodeIgnoreCase(normalizedCode)) {
                throw new BusinessException("Mã gói dịch vụ '" + normalizedCode + "' đã tồn tại");
            }
        } else {
            if (serviceTypeRepository.existsByCodeIgnoreCase(normalizedCode)) {
                throw new BusinessException("Mã gói dịch vụ '" + normalizedCode + "' đã tồn tại");
            }
            serviceType = new ServiceType();
            serviceType.setActive(true);
        }

        serviceType.setCode(normalizedCode);
        serviceType.setName(request.getName().trim());
        serviceType.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        serviceType.setBaseFee(request.getBaseFee());
        serviceType.setBaseWeightKg(request.getBaseWeightKg());
        serviceType.setExtraFeePerHalfKg(request.getExtraFeePerHalfKg());
        serviceType.setInterProvinceSurcharge(request.getInterProvinceSurcharge());
        serviceType.setCodFeePercent(request.getCodFeePercent());
        serviceType.setEstimatedDaysMin(request.getEstimatedDaysMin());
        serviceType.setEstimatedDaysMax(request.getEstimatedDaysMax());

        return serviceTypeRepository.save(serviceType);
    }

    @Transactional
    public ServiceType toggleActive(Long id) {
        ServiceType serviceType = getById(id);
        serviceType.setActive(!Boolean.TRUE.equals(serviceType.getActive()));
        return serviceTypeRepository.save(serviceType);
    }
}
