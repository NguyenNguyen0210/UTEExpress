package com.uteexpress.service;

import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;
import com.uteexpress.config.AppProperties;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.enums.HubType;
import com.uteexpress.enums.InspectionPolicy;
import com.uteexpress.enums.PayerType;
import com.uteexpress.enums.PaymentMethod;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaybillPdfServiceTest {

    @Mock
    private ShipmentService shipmentService;

    private AppProperties appProperties;
    private WaybillPdfService waybillPdfService;
    private Shipment vietnameseFragileCodShipment;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.setBaseUrl("http://localhost:8080");

        waybillPdfService = new WaybillPdfService(appProperties, shipmentService);

        Hub originHub = Hub.builder()
                .id(1L)
                .code("HCM-TD01")
                .name("Bưu cục Thủ Đức")
                .type(HubType.POST_OFFICE)
                .province("TP. Hồ Chí Minh")
                .district("TP. Thủ Đức")
                .build();

        Hub destHub = Hub.builder()
                .id(6L)
                .code("HN-CG01")
                .name("Bưu cục Cầu Giấy")
                .type(HubType.POST_OFFICE)
                .province("Hà Nội")
                .district("Quận Cầu Giấy")
                .build();

        ServiceType expressService = ServiceType.builder()
                .id(2L)
                .code("EXPRESS")
                .name("Nhanh")
                .build();

        vietnameseFragileCodShipment = Shipment.builder()
                .id(101L)
                .trackingCode("UTE261120A8K4P2")
                .serviceType(expressService)
                .originHub(originHub)
                .destHub(destHub)
                .currentHub(originHub)
                .senderName("Cửa hàng Công nghệ Hoàng Nam — TP. Thủ Đức")
                .senderPhone("0901234567")
                .senderProvince("TP. Hồ Chí Minh")
                .senderDistrict("TP. Thủ Đức")
                .senderAddress("01 Võ Văn Ngân, Phường Linh Chiểu")
                .receiverName("Trần Minh Ánh Tuyết")
                .receiverPhone("0987654321")
                .receiverProvince("Hà Nội")
                .receiverDistrict("Quận Cầu Giấy")
                .receiverAddress("123 Xuân Thủy, Phường Dịch Vọng Hậu")
                .itemName("Bộ ấm trà gốm sứ Bát Tràng cao cấp (dễ vỡ)")
                .actualWeightKg(new BigDecimal("1.50"))
                .chargeableWeightKg(new BigDecimal("2.00"))
                .isFragile(true)
                .inspectionPolicy(InspectionPolicy.ALLOW_VIEW_NO_TRY)
                .note("Giao giờ hành chính, gọi điện trước 15 phút")
                .shippingFee(new BigDecimal("40000"))
                .codFee(new BigDecimal("5000"))
                .totalFee(new BigDecimal("45000"))
                .codAmount(new BigDecimal("500000"))
                .payerType(PayerType.SENDER)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.PAID)
                .status(ShipmentStatus.CREATED)
                .build();
    }

    @Test
    @DisplayName("TC-MEDIA-04: generateWaybillPdf renders valid single-page A6 PDF with Vietnamese diacritics, fragile badge, and COD > 0")
    void tcMedia04_generateWaybillPdf_A6_validPdfBytesAndPageSize() throws Exception {
        when(shipmentService.calculateReceiverCollectAmount(vietnameseFragileCodShipment))
                .thenReturn(new BigDecimal("500000"));

        byte[] pdfBytes = waybillPdfService.generateWaybillPdf(vietnameseFragileCodShipment, "A6");

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(5_000);
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        try (PdfReader reader = new PdfReader(pdfBytes)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            Rectangle rect = reader.getPageSize(1);
            assertThat(rect.getWidth()).isCloseTo(PageSize.A6.getWidth(), org.assertj.core.data.Offset.offset(1.0f));
            assertThat(rect.getHeight()).isCloseTo(PageSize.A6.getHeight(), org.assertj.core.data.Offset.offset(1.0f));
        }
    }

    @Test
    @DisplayName("TC-MEDIA-04: generateWaybillPdf renders valid single-page A5 PDF with Vietnamese diacritics, fragile badge, and COD > 0")
    void tcMedia04_generateWaybillPdf_A5_validPdfBytesAndPageSize() throws Exception {
        when(shipmentService.calculateReceiverCollectAmount(vietnameseFragileCodShipment))
                .thenReturn(new BigDecimal("500000"));

        byte[] pdfBytes = waybillPdfService.generateWaybillPdf(vietnameseFragileCodShipment, "A5");

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(5_000);
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        try (PdfReader reader = new PdfReader(pdfBytes)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            Rectangle rect = reader.getPageSize(1);
            assertThat(rect.getWidth()).isCloseTo(PageSize.A5.getWidth(), org.assertj.core.data.Offset.offset(1.0f));
            assertThat(rect.getHeight()).isCloseTo(PageSize.A5.getHeight(), org.assertj.core.data.Offset.offset(1.0f));
        }
    }

    @Test
    @DisplayName("generateWaybillPdf handles prepaid shipment (receiverCollectAmount == 0) and defaults unknown size to A6")
    void generateWaybillPdf_prepaidZeroCollectAndDefaultSize_validSinglePageA6() throws Exception {
        vietnameseFragileCodShipment.setCodAmount(BigDecimal.ZERO);
        vietnameseFragileCodShipment.setIsFragile(false);
        vietnameseFragileCodShipment.setNote(null);
        when(shipmentService.calculateReceiverCollectAmount(vietnameseFragileCodShipment))
                .thenReturn(BigDecimal.ZERO);

        byte[] pdfBytes = waybillPdfService.generateWaybillPdf(vietnameseFragileCodShipment, null);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(5_000);
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        try (PdfReader reader = new PdfReader(pdfBytes)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            Rectangle rect = reader.getPageSize(1);
            assertThat(rect.getWidth()).isCloseTo(PageSize.A6.getWidth(), org.assertj.core.data.Offset.offset(1.0f));
        }
    }
}
