package com.uteexpress.service;

import com.uteexpress.dto.publicview.PublicTrackingItemDto;
import com.uteexpress.dto.publicview.PublicTrackingResponse;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.ShipmentTrackingLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicTrackingServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentTrackingLogRepository shipmentTrackingLogRepository;

    @InjectMocks
    private PublicTrackingService publicTrackingService;

    @Test
    @DisplayName("maskPhone should keep first 3 and last 3 digits and mask middle with ****")
    void shouldMaskPhoneCorrectly() {
        assertThat(publicTrackingService.maskPhone("0901234567")).isEqualTo("090****567");
        assertThat(publicTrackingService.maskPhone("0987654321")).isEqualTo("098****321");
        assertThat(publicTrackingService.maskPhone(" 02838968641 ")).isEqualTo("028****641");
        assertThat(publicTrackingService.maskPhone("12345")).isEqualTo("12****");
        assertThat(publicTrackingService.maskPhone("123")).isEqualTo("****");
        assertThat(publicTrackingService.maskPhone("")).isEmpty();
        assertThat(publicTrackingService.maskPhone(null)).isEmpty();
    }

    @Test
    @DisplayName("maskName should replace last word with *** or mask single word")
    void shouldMaskNameCorrectly() {
        assertThat(publicTrackingService.maskName("Trần Minh Anh")).isEqualTo("Trần Minh ***");
        assertThat(publicTrackingService.maskName("Shop Hoàng Nam Tech")).isEqualTo("Shop Hoàng Nam ***");
        assertThat(publicTrackingService.maskName("Nguyễn An")).isEqualTo("Nguyễn ***");
        assertThat(publicTrackingService.maskName("Nam")).isEqualTo("N***");
        assertThat(publicTrackingService.maskName("   ")).isEmpty();
        assertThat(publicTrackingService.maskName(null)).isEmpty();
    }

    @Test
    @DisplayName("TC-WS-02: Multi-code lookup returns 2 masked shipments and 1 not-found code")
    void shouldLookupMultipleCodesWithMaskingAndNotFoundList() {
        Hub originHub = Hub.builder().id(1L).code("HCM-TD01").name("Bưu cục Thủ Đức").build();
        Hub destHub = Hub.builder().id(6L).code("HN-CG01").name("Bưu cục Cầu Giấy").build();
        ServiceType express = ServiceType.builder().id(2L).code("EXPRESS").name("Nhanh").build();

        OffsetDateTime now = OffsetDateTime.now();
        Shipment s1 = Shipment.builder()
                .id(101L)
                .trackingCode("UTE261120A8K4P2")
                .serviceType(express)
                .senderName("Shop Hoàng Nam Tech")
                .senderPhone("0901234567")
                .senderProvince("TP. Hồ Chí Minh")
                .senderDistrict("TP. Thủ Đức")
                .senderAddress("01 Võ Văn Ngân, Phường Linh Chiểu")
                .receiverName("Trần Minh Anh")
                .receiverPhone("0987654321")
                .receiverProvince("Hà Nội")
                .receiverDistrict("Quận Cầu Giấy")
                .receiverAddress("123 Xuân Thủy, Phường Dịch Vọng Hậu")
                .originHub(originHub)
                .destHub(destHub)
                .currentHub(originHub)
                .itemName("Tai nghe Bluetooth Sony WH-1000XM5")
                .chargeableWeightKg(new BigDecimal("2.00"))
                .isFragile(true)
                .codAmount(new BigDecimal("500000"))
                .podImageKey("pod/2026/101.jpg")
                .status(ShipmentStatus.CREATED)
                .createdAt(now.minusHours(3))
                .updatedAt(now.minusHours(2))
                .build();

        Shipment s2 = Shipment.builder()
                .id(102L)
                .trackingCode("UTE261119B7M2Q9")
                .serviceType(express)
                .senderName("Shop Hoàng Nam Tech")
                .senderPhone("0901234567")
                .senderProvince("TP. Hồ Chí Minh")
                .senderDistrict("TP. Thủ Đức")
                .senderAddress("01 Võ Văn Ngân")
                .receiverName("Lê Hoàng Long")
                .receiverPhone("0912345678")
                .receiverProvince("Hà Nội")
                .receiverDistrict("Quận Cầu Giấy")
                .receiverAddress("88 Cầu Giấy")
                .originHub(originHub)
                .destHub(destHub)
                .currentHub(destHub)
                .itemName("Bàn phím cơ Keychron K8 Pro")
                .chargeableWeightKg(new BigDecimal("1.50"))
                .isFragile(false)
                .codAmount(new BigDecimal("800000"))
                .status(ShipmentStatus.IN_TRANSIT)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusHours(6))
                .build();

        ShipmentTrackingLog log1 = ShipmentTrackingLog.builder()
                .id(1L)
                .shipment(s1)
                .status(ShipmentStatus.CREATED)
                .hub(originHub)
                .title("Đã tạo vận đơn")
                .description("Chờ phân công Shipper lấy hàng")
                .createdAt(now.minusHours(3))
                .build();

        when(shipmentRepository.findByTrackingCodeIn(List.of("UTE261120A8K4P2", "UTE999999NOTFND", "UTE261119B7M2Q9")))
                .thenReturn(List.of(s2, s1));
        when(shipmentTrackingLogRepository.findByShipmentIdOrderByCreatedAtDescIdDesc(101L))
                .thenReturn(List.of(log1));
        when(shipmentTrackingLogRepository.findByShipmentIdOrderByCreatedAtDescIdDesc(102L))
                .thenReturn(List.of());

        PublicTrackingResponse response = publicTrackingService.lookupByCodes(
                "  ute261120a8k4p2 , UTE999999NOTFND\nUTE261119B7M2Q9 , UTE261120A8K4P2  "
        );

        assertThat(response.warningMessage()).isNull();
        assertThat(response.notFoundCodes()).containsExactly("UTE999999NOTFND");
        assertThat(response.shipments()).hasSize(2);

        PublicTrackingItemDto first = response.shipments().get(0);
        assertThat(first.trackingCode()).isEqualTo("UTE261120A8K4P2");
        assertThat(first.progressStep()).isEqualTo(1);
        assertThat(first.maskedSenderName()).isEqualTo("Shop Hoàng Nam ***");
        assertThat(first.maskedSenderPhone()).isEqualTo("090****567");
        assertThat(first.senderRegion()).isEqualTo("TP. Thủ Đức, TP. Hồ Chí Minh");
        assertThat(first.maskedReceiverName()).isEqualTo("Trần Minh ***");
        assertThat(first.maskedReceiverPhone()).isEqualTo("098****321");
        assertThat(first.receiverRegion()).isEqualTo("Quận Cầu Giấy, Hà Nội");
        assertThat(first.originHubCode()).isEqualTo("HCM-TD01");
        assertThat(first.destHubCode()).isEqualTo("HN-CG01");
        assertThat(first.currentHubName()).isEqualTo("Bưu cục Thủ Đức (HCM-TD01)");
        assertThat(first.fragile()).isTrue();
        assertThat(first.logs()).hasSize(1);
        assertThat(first.logs().get(0).hubName()).isEqualTo("Bưu cục Thủ Đức (HCM-TD01)");

        // Verify full phone & street address are never leaked in DTO values (BR-11)
        assertThat(first.toString())
                .doesNotContain("0901234567")
                .doesNotContain("0987654321")
                .doesNotContain("01 Võ Văn Ngân")
                .doesNotContain("123 Xuân Thủy")
                .doesNotContain("500000")
                .doesNotContain("pod/2026/101.jpg");

        PublicTrackingItemDto second = response.shipments().get(1);
        assertThat(second.trackingCode()).isEqualTo("UTE261119B7M2Q9");
        assertThat(second.progressStep()).isEqualTo(3);
    }

    @Test
    @DisplayName("BR-14 & TC-WS-02: More than 5 codes should truncate to first 5 and set warningMessage")
    @SuppressWarnings("unchecked")
    void shouldTruncateWhenExceedingFiveCodesWithWarningMessage() {
        when(shipmentRepository.findByTrackingCodeIn(anyCollection())).thenReturn(List.of());

        String rawInput = "UTE001, UTE002, UTE003, UTE004, UTE005, UTE006, UTE007";
        PublicTrackingResponse response = publicTrackingService.lookupByCodes(rawInput);

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(shipmentRepository).findByTrackingCodeIn(captor.capture());

        assertThat(captor.getValue()).containsExactly("UTE001", "UTE002", "UTE003", "UTE004", "UTE005");
        assertThat(response.warningMessage())
                .isEqualTo("Hệ thống hỗ trợ tra cứu tối đa 5 mã vận đơn mỗi lần. Đã hiển thị 5 mã đầu tiên.");
        assertThat(response.notFoundCodes())
                .containsExactly("UTE001", "UTE002", "UTE003", "UTE004", "UTE005");
    }

    @Test
    @DisplayName("BR-11 Security: PublicTrackingItemDto must never define COD, POD, full phone, or street address fields")
    void shouldNotExposeSensitiveFieldsInPublicTrackingItemDto() {
        Set<String> fieldNames = Arrays.stream(PublicTrackingItemDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(fieldNames)
                .doesNotContain(
                        "codAmount",
                        "codFee",
                        "codStatus",
                        "podImageKey",
                        "senderAddress",
                        "receiverAddress",
                        "senderPhone",
                        "receiverPhone",
                        "senderName",
                        "receiverName"
                );
    }

    @Test
    @DisplayName("resolveProgressStep should map all shipment statuses to expected 0-5 stepper values")
    void shouldResolveProgressStepCorrectly() {
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.PENDING_PAYMENT)).isEqualTo(1);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.CREATED)).isEqualTo(1);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.ASSIGNED_PICKUP)).isEqualTo(1);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.PICKED_UP)).isEqualTo(2);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.IN_ORIGIN_HUB)).isEqualTo(2);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.IN_TRANSIT)).isEqualTo(3);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.IN_DEST_HUB)).isEqualTo(3);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.OUT_FOR_DELIVERY)).isEqualTo(4);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.DELIVERY_FAILED)).isEqualTo(4);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.DELIVERED)).isEqualTo(5);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.RETURNING)).isEqualTo(0);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.RETURNED)).isEqualTo(0);
        assertThat(publicTrackingService.resolveProgressStep(ShipmentStatus.CANCELLED)).isEqualTo(0);
    }

    @Test
    @DisplayName("Empty or blank input should return empty response without querying DB")
    void shouldHandleBlankInputGracefully() {
        PublicTrackingResponse response = publicTrackingService.lookupByCodes(" ,  \n  ");
        assertThat(response.shipments()).isEmpty();
        assertThat(response.notFoundCodes()).isEmpty();
        assertThat(response.warningMessage()).isNull();
        verifyNoInteractions(shipmentRepository, shipmentTrackingLogRepository);
    }
}
