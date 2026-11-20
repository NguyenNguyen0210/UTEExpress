package com.uteexpress.dto.publicview;

import java.util.List;

public record PublicTrackingResponse(
        List<PublicTrackingItemDto> shipments,
        List<String> notFoundCodes,
        String warningMessage
) {
}
