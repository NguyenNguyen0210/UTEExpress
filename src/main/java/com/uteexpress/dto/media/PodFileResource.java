package com.uteexpress.dto.media;

public record PodFileResource(
        byte[] bytes,
        String contentType,
        String filename
) {
}
