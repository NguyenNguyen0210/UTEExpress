package com.uteexpress.service;

import com.uteexpress.config.AppProperties;
import com.uteexpress.config.R2Config;
import com.uteexpress.dto.media.PodFileResource;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class R2StorageService {

    private static final String POD_KEY_PREFIX = "pod/";
    private static final long DEFAULT_POD_MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final String ERR_EMPTY_FILE = "Bắt buộc phải đính kèm ảnh chứng từ giao hàng (POD)";
    private static final String ERR_SIZE_EXCEEDED = "Dung lượng ảnh POD vượt quá giới hạn tối đa 5MB";
    private static final String ERR_INVALID_FORMAT = "Định dạng file không hợp lệ. Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP";

    private static final Map<String, String> ALLOWED_MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private static final Map<String, Set<String>> ALLOWED_MIME_EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp")
    );

    private final S3Client r2S3Client;
    private final R2Config r2Config;
    private final AppProperties appProperties;

    public void validatePodImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ERR_EMPTY_FILE);
        }

        long maxSizeBytes = appProperties.getPodMaxSizeBytes() > 0
                ? appProperties.getPodMaxSizeBytes()
                : DEFAULT_POD_MAX_SIZE_BYTES;
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException(ERR_SIZE_EXCEEDED);
        }

        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !ALLOWED_MIME_TO_EXT.containsKey(contentType)) {
            throw new BusinessException(ERR_INVALID_FORMAT);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.isBlank()) {
            if (originalFilename.contains("..")
                    || originalFilename.contains("/")
                    || originalFilename.contains("\\")) {
                throw new BusinessException(ERR_INVALID_FORMAT);
            }

            int lastDot = originalFilename.lastIndexOf('.');
            if (lastDot >= 0) {
                if (lastDot == originalFilename.length() - 1) {
                    throw new BusinessException(ERR_INVALID_FORMAT);
                }
                String ext = originalFilename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
                Set<String> allowedExts = ALLOWED_MIME_EXTENSIONS.get(contentType);
                if (allowedExts == null || !allowedExts.contains(ext)) {
                    throw new BusinessException(ERR_INVALID_FORMAT);
                }
            }
        }
    }

    public String uploadPodImage(MultipartFile file) {
        validatePodImage(file);

        String contentType = normalizeContentType(file.getContentType());
        String extension = ALLOWED_MIME_TO_EXT.get(contentType);
        String key = POD_KEY_PREFIX + UUID.randomUUID() + "." + extension;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(r2Config.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .build();

            r2S3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            return key;
        } catch (IOException | SdkException ex) {
            log.error("Failed to upload POD image to Cloudflare R2 (key={}): {}", key, ex.getMessage(), ex);
            throw new BusinessException("Không thể tải ảnh POD lên Cloudflare R2: " + ex.getMessage());
        }
    }

    public PodFileResource downloadPodImage(String objectKey) {
        String normalizedKey = validateObjectKey(objectKey);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(normalizedKey)
                .build();

        try {
            ResponseBytes<GetObjectResponse> responseBytes = r2S3Client.getObjectAsBytes(request);
            byte[] bytes = responseBytes.asByteArray();
            String s3ContentType = responseBytes.response() != null
                    ? responseBytes.response().contentType()
                    : null;
            String contentType = resolveDownloadContentType(s3ContentType, normalizedKey);
            String filename = normalizedKey.substring(POD_KEY_PREFIX.length());
            return new PodFileResource(bytes, contentType, filename);
        } catch (NoSuchKeyException ex) {
            throw new ResourceNotFoundException("Không tìm thấy file ảnh POD trên Cloudflare R2");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ResourceNotFoundException("Không tìm thấy file ảnh POD trên Cloudflare R2");
            }
            log.error("Failed to download POD image from Cloudflare R2 (key={}): {}", normalizedKey, ex.getMessage(), ex);
            throw new BusinessException("Không thể tải ảnh POD từ Cloudflare R2: " + ex.getMessage());
        } catch (SdkException ex) {
            log.error("Failed to download POD image from Cloudflare R2 (key={}): {}", normalizedKey, ex.getMessage(), ex);
            throw new BusinessException("Không thể tải ảnh POD từ Cloudflare R2: " + ex.getMessage());
        }
    }

    private String validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException("Đường dẫn ảnh POD không hợp lệ");
        }
        String normalizedKey = objectKey.trim();
        if (!normalizedKey.startsWith(POD_KEY_PREFIX)
                || normalizedKey.length() <= POD_KEY_PREFIX.length()
                || normalizedKey.contains("..")
                || normalizedKey.contains("\\")
                || normalizedKey.substring(POD_KEY_PREFIX.length()).contains("/")) {
            throw new BusinessException("Đường dẫn ảnh POD không hợp lệ");
        }
        return normalizedKey;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolonIdx = normalized.indexOf(';');
        if (semicolonIdx >= 0) {
            normalized = normalized.substring(0, semicolonIdx).trim();
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveDownloadContentType(String s3ContentType, String objectKey) {
        String normalizedS3Type = normalizeContentType(s3ContentType);
        if (normalizedS3Type != null && ALLOWED_MIME_TO_EXT.containsKey(normalizedS3Type)) {
            return normalizedS3Type;
        }

        int lastDot = objectKey.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < objectKey.length() - 1) {
            String ext = objectKey.substring(lastDot + 1).toLowerCase(Locale.ROOT);
            return switch (ext) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "webp" -> "image/webp";
                default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
            };
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
