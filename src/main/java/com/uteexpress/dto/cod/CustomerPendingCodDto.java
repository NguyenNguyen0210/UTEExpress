package com.uteexpress.dto.cod;

import com.uteexpress.entity.Shipment;

import java.math.BigDecimal;
import java.util.List;

public record CustomerPendingCodDto(
        Long customerId,
        String customerName,
        String customerEmail,
        String bankName,
        String bankAccountNumber,
        String bankAccountHolder,
        List<Shipment> shipments,
        BigDecimal totalCodAmount,
        BigDecimal totalShippingDeduction,
        BigDecimal netAmount
) {
}
