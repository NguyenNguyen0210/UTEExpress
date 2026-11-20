package com.uteexpress.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors
) {
    public static ApiErrorResponse of(int status, String error, String message) {
        return new ApiErrorResponse(OffsetDateTime.now(), status, error, message, null);
    }

    public static ApiErrorResponse of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiErrorResponse(OffsetDateTime.now(), status, error, message, fieldErrors);
    }
}
