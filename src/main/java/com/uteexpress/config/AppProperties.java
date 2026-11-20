package com.uteexpress.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
public class AppProperties {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.pod-max-size-bytes:5242880}")
    private long podMaxSizeBytes;
}
