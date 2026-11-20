package com.uteexpress.dto.payment;

import com.uteexpress.entity.Shipment;

public record VnPayProcessResult(
        boolean validSignature,
        boolean paymentSuccess,
        boolean alreadyProcessed,
        String rspCode,
        String message,
        Shipment shipment
) {
}
