package com.uteexpress.config;

import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@Getter
@Setter
public class R2Config {

    @Value("${app.r2.endpoint}")
    private String endpoint;

    @Value("${app.r2.access-key-id}")
    private String accessKeyId;

    @Value("${app.r2.secret-access-key}")
    private String secretAccessKey;

    @Value("${app.r2.bucket-name}")
    private String bucketName;

    @Bean
    public S3Client r2S3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .region(Region.of("auto"))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
