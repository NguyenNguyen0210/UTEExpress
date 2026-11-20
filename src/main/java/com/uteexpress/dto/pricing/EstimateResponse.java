package com.uteexpress.dto.pricing;

import java.math.BigDecimal;
import java.util.List;

public record EstimateResponse(
        boolean interProvince,
        Long originHubId,
        String originHubCode,
        String originHubName,
        Long destHubId,
        String destHubCode,
        String destHubName,
        BigDecimal volumetricWeightKg,
        BigDecimal chargeableWeightKg,
        List<ServiceOptionQuote> options
) {
}
