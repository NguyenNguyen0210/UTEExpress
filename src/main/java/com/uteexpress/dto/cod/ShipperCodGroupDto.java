package com.uteexpress.dto.cod;

import com.uteexpress.entity.Shipment;

import java.math.BigDecimal;
import java.util.List;

public record ShipperCodGroupDto(
        Long shipperId,
        String shipperName,
        String shipperPhone,
        String hubCode,
        List<Shipment> shipments,
        BigDecimal totalCodAmount,
        BigDecimal totalReceiverCollectAmount
) {
}
