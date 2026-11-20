package com.uteexpress.service;

import com.uteexpress.dto.admin.HubFormRequest;
import com.uteexpress.entity.Hub;
import com.uteexpress.enums.HubType;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HubService {

    private final HubRepository hubRepository;

    public List<Hub> findAll() {
        return hubRepository.findAllByOrderByIdAsc();
    }

    public List<Hub> findActiveHubs() {
        return hubRepository.findByActiveTrueOrderByProvinceAscNameAsc();
    }

    public List<Hub> searchPublicHubs(String province, HubType type) {
        boolean hasProvince = province != null && !province.isBlank();
        if (hasProvince && type != null) {
            return hubRepository.findByProvinceIgnoreCaseAndTypeAndActiveTrueOrderByNameAsc(province.trim(), type);
        }
        if (hasProvince) {
            return hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc(province.trim());
        }
        if (type != null) {
            return hubRepository.findByTypeAndActiveTrueOrderByProvinceAscNameAsc(type);
        }
        return hubRepository.findByActiveTrueOrderByProvinceAscNameAsc();
    }

    public List<String> getActiveProvinces() {
        return hubRepository.findByActiveTrueOrderByProvinceAscNameAsc().stream()
                .map(Hub::getProvince)
                .distinct()
                .toList();
    }

    public Hub getById(Long id) {
        return hubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bưu cục với ID: " + id));
    }

    @Transactional
    public Hub saveOrUpdate(HubFormRequest request) {
        String normalizedCode = request.getCode().trim().toUpperCase();
        Hub hub;

        if (request.getId() != null) {
            hub = getById(request.getId());
            if (!hub.getCode().equalsIgnoreCase(normalizedCode)
                    && hubRepository.existsByCodeIgnoreCase(normalizedCode)) {
                throw new BusinessException("Mã bưu cục '" + normalizedCode + "' đã tồn tại");
            }
        } else {
            if (hubRepository.existsByCodeIgnoreCase(normalizedCode)) {
                throw new BusinessException("Mã bưu cục '" + normalizedCode + "' đã tồn tại");
            }
            hub = new Hub();
            hub.setActive(true);
        }

        hub.setCode(normalizedCode);
        hub.setName(request.getName().trim());
        hub.setType(request.getType());
        hub.setProvince(request.getProvince().trim());
        hub.setDistrict(request.getDistrict().trim());
        hub.setAddress(request.getAddress().trim());
        hub.setPhone(request.getPhone().trim());

        return hubRepository.save(hub);
    }

    @Transactional
    public Hub toggleActive(Long id) {
        Hub hub = getById(id);
        hub.setActive(!Boolean.TRUE.equals(hub.getActive()));
        return hubRepository.save(hub);
    }
}
