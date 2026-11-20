package com.uteexpress.dto.pricing;

import java.math.BigDecimal;

public record ServiceOptionQuote(
        Long serviceTypeId,
        String code,
        String name,
        String description,
        BigDecimal shippingFee,
        BigDecimal codFee,
        BigDecimal totalFee,
        String estimatedDays
) {
}
