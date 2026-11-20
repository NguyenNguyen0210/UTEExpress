package com.uteexpress.service;

import com.uteexpress.dto.pricing.EstimateRequest;
import com.uteexpress.dto.pricing.EstimateResponse;
import com.uteexpress.dto.pricing.ServiceOptionQuote;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.enums.HubType;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.repository.HubRepository;
import com.uteexpress.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingService {

    private static final BigDecimal VOLUMETRIC_DIVISOR = new BigDecimal("5000");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final HubRepository hubRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional(readOnly = true)
    public Hub resolveHubForProvince(String province) {
        if (province == null || province.isBlank()) {
            throw new BusinessException("Vui lòng chọn tỉnh/thành phố hợp lệ");
        }
        String normalized = province.trim();
        List<Hub> hubs = hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc(normalized);
        if (hubs == null || hubs.isEmpty()) {
            throw new BusinessException("Chưa hỗ trợ mạng lưới bưu cục tại tỉnh/thành: " + normalized);
        }
        return hubs.stream()
                .filter(hub -> hub.getType() == HubType.POST_OFFICE)
                .findFirst()
                .orElse(hubs.get(0));
    }

    public BigDecimal calculateVolumetricWeight(Integer lengthCm, Integer widthCm, Integer heightCm) {
        if (lengthCm == null || widthCm == null || heightCm == null
                || lengthCm <= 0 || widthCm <= 0 || heightCm <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal volume = BigDecimal.valueOf(lengthCm)
                .multiply(BigDecimal.valueOf(widthCm))
                .multiply(BigDecimal.valueOf(heightCm));
        return volume.divide(VOLUMETRIC_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateChargeableWeight(BigDecimal actualWeightKg, BigDecimal volumetricWeightKg) {
        if (actualWeightKg == null || actualWeightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Trọng lượng thực phải lớn hơn 0");
        }
        BigDecimal effectiveVolumetric = volumetricWeightKg != null ? volumetricWeightKg : BigDecimal.ZERO;
        BigDecimal maxWeight = actualWeightKg.max(effectiveVolumetric);
        BigDecimal halfKgSteps = maxWeight.multiply(TWO).setScale(0, RoundingMode.CEILING);
        return halfKgSteps.divide(TWO, 2, RoundingMode.HALF_UP);
    }

    public ServiceOptionQuote calculateQuote(ServiceType serviceType,
                                             BigDecimal chargeableWeightKg,
                                             boolean interProvince,
                                             BigDecimal codAmount) {
        BigDecimal baseWeight = serviceType.getBaseWeightKg() != null
                ? serviceType.getBaseWeightKg()
                : BigDecimal.ONE;

        BigDecimal extraWeight = chargeableWeightKg.compareTo(baseWeight) > 0
                ? chargeableWeightKg.subtract(baseWeight)
                : BigDecimal.ZERO;

        long extraHalfKgSteps = extraWeight.compareTo(BigDecimal.ZERO) > 0
                ? extraWeight.multiply(TWO).setScale(0, RoundingMode.CEILING).longValue()
                : 0L;

        BigDecimal extraFee = serviceType.getExtraFeePerHalfKg()
                .multiply(BigDecimal.valueOf(extraHalfKgSteps));

        BigDecimal surcharge = (interProvince && serviceType.getInterProvinceSurcharge() != null)
                ? serviceType.getInterProvinceSurcharge()
                : BigDecimal.ZERO;

        BigDecimal shippingFee = serviceType.getBaseFee()
                .add(extraFee)
                .add(surcharge)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal effectiveCod = (codAmount != null && codAmount.compareTo(BigDecimal.ZERO) > 0)
                ? codAmount
                : BigDecimal.ZERO;

        BigDecimal codFeePercent = serviceType.getCodFeePercent() != null
                ? serviceType.getCodFeePercent()
                : BigDecimal.ZERO;

        BigDecimal codFee = effectiveCod.multiply(codFeePercent)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);

        BigDecimal totalFee = shippingFee.add(codFee);

        String estimatedDays = serviceType.getEstimatedDaysMin() + " - " + serviceType.getEstimatedDaysMax() + " ngày";

        return new ServiceOptionQuote(
                serviceType.getId(),
                serviceType.getCode(),
                serviceType.getName(),
                serviceType.getDescription(),
                shippingFee,
                codFee,
                totalFee,
                estimatedDays
        );
    }

    @Transactional(readOnly = true)
    public EstimateResponse estimate(EstimateRequest request) {
        Hub originHub = resolveHubForProvince(request.getSenderProvince());
        Hub destHub = resolveHubForProvince(request.getReceiverProvince());

        boolean interProvince = !request.getSenderProvince().trim()
                .equalsIgnoreCase(request.getReceiverProvince().trim());

        BigDecimal volumetricWeightKg = calculateVolumetricWeight(
                request.getLengthCm(),
                request.getWidthCm(),
                request.getHeightCm()
        );
        BigDecimal chargeableWeightKg = calculateChargeableWeight(
                request.getActualWeightKg(),
                volumetricWeightKg
        );

        List<ServiceType> services;
        if (request.getServiceTypeId() != null) {
            ServiceType serviceType = serviceTypeRepository.findById(request.getServiceTypeId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy gói dịch vụ với ID: " + request.getServiceTypeId()));
            if (!Boolean.TRUE.equals(serviceType.getActive())) {
                throw new BusinessException("Gói dịch vụ '" + serviceType.getName() + "' hiện đang tạm ngưng hoạt động");
            }
            services = List.of(serviceType);
        } else {
            services = serviceTypeRepository.findByActiveTrueOrderByBaseFeeAsc();
            if (services.isEmpty()) {
                throw new BusinessException("Hiện chưa có gói dịch vụ vận chuyển nào đang hoạt động");
            }
        }

        List<ServiceOptionQuote> options = services.stream()
                .map(serviceType -> calculateQuote(serviceType, chargeableWeightKg, interProvince, request.getCodAmount()))
                .toList();

        return new EstimateResponse(
                interProvince,
                originHub.getId(),
                originHub.getCode(),
                originHub.getName(),
                destHub.getId(),
                destHub.getCode(),
                destHub.getName(),
                volumetricWeightKg,
                chargeableWeightKg,
                options
        );
    }
}
