package com.uteexpress.service;

import com.uteexpress.config.AppProperties;
import com.uteexpress.config.R2Config;
import com.uteexpress.dto.media.PodFileResource;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    private static final String BUCKET_NAME = "uteexpress-pod";
    private static final long MAX_POD_BYTES = 5_242_880L;

    @Mock
    private S3Client r2S3Client;

    @Mock
    private R2Config r2Config;

    @Mock
    private AppProperties appProperties;

    private R2StorageService r2StorageService;

    @BeforeEach
    void setUp() {
        r2StorageService = new R2StorageService(r2S3Client, r2Config, appProperties);
        lenient().when(r2Config.getBucketName()).thenReturn(BUCKET_NAME);
        lenient().when(appProperties.getPodMaxSizeBytes()).thenReturn(MAX_POD_BYTES);
    }

    @Test
    @DisplayName("TC-MEDIA-01: Null or empty MultipartFile throws BusinessException and does not call R2")
    void tcMedia01_nullOrEmptyFileThrowsBusinessException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "podImage", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> r2StorageService.uploadPodImage(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bắt buộc phải đính kèm ảnh chứng từ giao hàng (POD)");

        assertThatThrownBy(() -> r2StorageService.uploadPodImage(emptyFile))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bắt buộc phải đính kèm ảnh chứng từ giao hàng (POD)");

        verify(r2S3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("TC-MEDIA-02: PDF file, >5MB JPG, mismatched .exe extension, and traversal filename are rejected")
    void tcMedia02_invalidMimeOversizedMismatchedExtensionThrowsBusinessException() {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "podImage", "invoice.pdf", "application/pdf", "%PDF-1.4 sample".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> r2StorageService.uploadPodImage(pdfFile))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Định dạng file không hợp lệ. Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP");

        byte[] oversizedBytes = new byte[(int) MAX_POD_BYTES + 1];
        oversizedBytes[0] = (byte) 0xFF;
        MockMultipartFile oversizedJpg = new MockMultipartFile(
                "podImage", "huge-photo.jpg", "image/jpeg", oversizedBytes);
        assertThatThrownBy(() -> r2StorageService.uploadPodImage(oversizedJpg))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Dung lượng ảnh POD vượt quá giới hạn tối đa 5MB");

        MockMultipartFile spoofedExe = new MockMultipartFile(
                "podImage", "malware.exe", "image/jpeg", new byte[]{1, 2, 3, 4});
        assertThatThrownBy(() -> r2StorageService.uploadPodImage(spoofedExe))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Định dạng file không hợp lệ. Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP");

        MockMultipartFile traversalFilename = new MockMultipartFile(
                "podImage", "../../etc/passwd.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
        assertThatThrownBy(() -> r2StorageService.uploadPodImage(traversalFilename))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Định dạng file không hợp lệ. Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP");

        verify(r2S3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("TC-MEDIA-03: Valid JPG upload stores object in uteexpress-pod with UUID key and image/jpeg")
    void tcMedia03_validJpgUploadCallsPutObject() {
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile jpgFile = new MockMultipartFile(
                "podImage", "original-name.jpg", "image/jpeg", content);

        when(r2S3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = r2StorageService.uploadPodImage(jpgFile);

        assertThat(key)
                .matches("^pod/[0-9a-f-]+\\.jpg$")
                .doesNotContain("original-name");

        ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(r2S3Client).putObject(reqCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = reqCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captured.key()).isEqualTo(key);
        assertThat(captured.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("TC-MEDIA-03: Valid PNG upload stores object in uteexpress-pod with UUID key and image/png")
    void tcMedia03_validPngUploadCallsPutObject() {
        byte[] content = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        MockMultipartFile pngFile = new MockMultipartFile(
                "podImage", "delivery-proof.png", "image/png", content);

        when(r2S3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = r2StorageService.uploadPodImage(pngFile);

        assertThat(key).matches("^pod/[0-9a-f-]+\\.png$");

        ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(r2S3Client).putObject(reqCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = reqCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captured.key()).isEqualTo(key);
        assertThat(captured.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("TC-MEDIA-03: Valid WEBP upload stores object in uteexpress-pod with UUID key and image/webp")
    void tcMedia03_validWebpUploadCallsPutObject() {
        byte[] content = "RIFF....WEBP".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile webpFile = new MockMultipartFile(
                "podImage", "pod-capture.webp", "image/webp", content);

        when(r2S3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = r2StorageService.uploadPodImage(webpFile);

        assertThat(key).matches("^pod/[0-9a-f-]+\\.webp$");

        ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(r2S3Client).putObject(reqCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = reqCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captured.key()).isEqualTo(key);
        assertThat(captured.contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("uploadPodImage wraps S3Exception cleanly into BusinessException")
    void uploadPodImageWrapsS3FailureIntoBusinessException() {
        MockMultipartFile jpgFile = new MockMultipartFile(
                "podImage", "pod.jpeg", "image/jpeg", new byte[]{1, 2, 3});
        when(r2S3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("R2 connection refused").build());

        assertThatThrownBy(() -> r2StorageService.uploadPodImage(jpgFile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể tải ảnh POD lên Cloudflare R2");
    }

    @Test
    @DisplayName("downloadPodImage rejects path traversal, blank keys, and non-pod prefixes")
    void downloadPodImageRejectsPathTraversalAndInvalidKeys() {
        assertThatThrownBy(() -> r2StorageService.downloadPodImage("pod/../secret.txt"))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> r2StorageService.downloadPodImage("other/sample.jpg"))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> r2StorageService.downloadPodImage("   "))
                .isInstanceOf(BusinessException.class);

        verify(r2S3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("downloadPodImage returns bytes, Content-Type, and filename for valid key (and extension fallback)")
    void downloadPodImageReturnsBytesAndContentType() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, 0x01, 0x02};
        String jpgKey = "pod/550e8400-e29b-41d4-a716-446655440000.jpg";

        ResponseBytes<GetObjectResponse> s3Response = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentType("image/jpeg").build(),
                jpegBytes);
        when(r2S3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(s3Response);

        PodFileResource resource = r2StorageService.downloadPodImage(jpgKey);

        assertThat(resource.bytes()).isEqualTo(jpegBytes);
        assertThat(resource.contentType()).isEqualTo("image/jpeg");
        assertThat(resource.filename()).isEqualTo("550e8400-e29b-41d4-a716-446655440000.jpg");

        ArgumentCaptor<GetObjectRequest> reqCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(r2S3Client).getObjectAsBytes(reqCaptor.capture());
        assertThat(reqCaptor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(reqCaptor.getValue().key()).isEqualTo(jpgKey);

        // Fallback Content-Type by extension when S3 response omits contentType
        byte[] webpBytes = "RIFF....WEBP".getBytes(StandardCharsets.US_ASCII);
        String webpKey = "pod/550e8400-e29b-41d4-a716-446655440001.webp";
        ResponseBytes<GetObjectResponse> noMimeResponse = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                webpBytes);
        when(r2S3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(noMimeResponse);

        PodFileResource webpResource = r2StorageService.downloadPodImage(webpKey);
        assertThat(webpResource.bytes()).isEqualTo(webpBytes);
        assertThat(webpResource.contentType()).isEqualTo("image/webp");
        assertThat(webpResource.filename()).isEqualTo("550e8400-e29b-41d4-a716-446655440001.webp");
    }

    @Test
    @DisplayName("downloadPodImage maps NoSuchKeyException to ResourceNotFoundException")
    void downloadPodImageMapsNoSuchKeyExceptionToResourceNotFound() {
        String missingKey = "pod/550e8400-e29b-41d4-a716-446655449999.png";
        when(r2S3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("NoSuchKey").build());

        assertThatThrownBy(() -> r2StorageService.downloadPodImage(missingKey))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy file ảnh POD trên Cloudflare R2");
    }
}
