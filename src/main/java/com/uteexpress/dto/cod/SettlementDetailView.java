package com.uteexpress.dto.cod;

import com.uteexpress.entity.CodSettlement;
import com.uteexpress.entity.Shipment;

import java.util.List;

public record SettlementDetailView(
        CodSettlement settlement,
        List<Shipment> shipments
) {
}
