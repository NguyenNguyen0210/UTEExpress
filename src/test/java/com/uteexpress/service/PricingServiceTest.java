package com.uteexpress.service;

import com.uteexpress.dto.pricing.EstimateRequest;
import com.uteexpress.dto.pricing.EstimateResponse;
import com.uteexpress.dto.pricing.ServiceOptionQuote;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.enums.HubType;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.repository.HubRepository;
import com.uteexpress.repository.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private HubRepository hubRepository;

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @InjectMocks
    private PricingService pricingService;

    private Hub hcmPostOffice;
    private Hub hcmSortingHub;
    private Hub hnPostOffice;
    private Hub danangSortingHub;

    private ServiceType standardService;
    private ServiceType expressService;
    private ServiceType samedayService;

    @BeforeEach
    void setUp() {
        hcmPostOffice = Hub.builder()
                .id(1L)
                .code("HCM-TD01")
                .name("Bưu cục Thủ Đức")
                .type(HubType.POST_OFFICE)
                .province("TP. Hồ Chí Minh")
                .district("TP. Thủ Đức")
                .active(true)
                .build();

        hcmSortingHub = Hub.builder()
                .id(2L)
                .code("HCM-Q101")
                .name("Kho Trung chuyển Miền Nam")
                .type(HubType.SORTING_HUB)
                .province("TP. Hồ Chí Minh")
                .district("Quận 1")
                .active(true)
                .build();

        hnPostOffice = Hub.builder()
                .id(6L)
                .code("HN-CG01")
                .name("Bưu cục Cầu Giấy")
                .type(HubType.POST_OFFICE)
                .province("Hà Nội")
                .district("Quận Cầu Giấy")
                .active(true)
                .build();

        danangSortingHub = Hub.builder()
                .id(5L)
                .code("DNG-HC01")
                .name("Kho Trung chuyển Miền Trung")
                .type(HubType.SORTING_HUB)
                .province("Đà Nẵng")
                .district("Quận Hải Châu")
                .active(true)
                .build();

        standardService = ServiceType.builder()
                .id(1L)
                .code("STANDARD")
                .name("Tiêu chuẩn")
                .description("Giao hàng tiết kiệm toàn quốc")
                .baseFee(new BigDecimal("20000"))
                .baseWeightKg(new BigDecimal("1.00"))
                .extraFeePerHalfKg(new BigDecimal("5000"))
                .interProvinceSurcharge(new BigDecimal("10000"))
                .codFeePercent(new BigDecimal("1.00"))
                .estimatedDaysMin(3)
                .estimatedDaysMax(5)
                .active(true)
                .build();

        expressService = ServiceType.builder()
                .id(2L)
                .code("EXPRESS")
                .name("Nhanh")
                .description("Giao hàng ưu tiên liên tỉnh & nội thành")
                .baseFee(new BigDecimal("30000"))
                .baseWeightKg(new BigDecimal("1.00"))
                .extraFeePerHalfKg(new BigDecimal("7000"))
                .interProvinceSurcharge(new BigDecimal("15000"))
                .codFeePercent(new BigDecimal("1.00"))
                .estimatedDaysMin(1)
                .estimatedDaysMax(2)
                .active(true)
                .build();

        samedayService = ServiceType.builder()
                .id(3L)
                .code("SAMEDAY")
                .name("Hỏa tốc")
                .description("Giao nhanh ưu tiên trong ngày hoặc sáng hôm sau")
                .baseFee(new BigDecimal("50000"))
                .baseWeightKg(new BigDecimal("1.00"))
                .extraFeePerHalfKg(new BigDecimal("10000"))
                .interProvinceSurcharge(new BigDecimal("30000"))
                .codFeePercent(new BigDecimal("1.50"))
                .estimatedDaysMin(0)
                .estimatedDaysMax(1)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("TC-PRICE-01: Intra-province STANDARD, actual 0.8kg, no dimensions, COD 0 -> chargeable 1.00, total 20000")
    void tcPrice01_intraProvinceStandardNoDimensionsNoCod() {
        when(hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc("TP. Hồ Chí Minh"))
                .thenReturn(List.of(hcmPostOffice, hcmSortingHub));
        when(serviceTypeRepository.findById(1L))
                .thenReturn(Optional.of(standardService));

        EstimateRequest request = EstimateRequest.builder()
                .senderProvince("TP. Hồ Chí Minh")
                .receiverProvince("TP. Hồ Chí Minh")
                .actualWeightKg(new BigDecimal("0.8"))
                .codAmount(BigDecimal.ZERO)
                .serviceTypeId(1L)
                .build();

        EstimateResponse response = pricingService.estimate(request);

        assertThat(response.interProvince()).isFalse();
        assertThat(response.originHubId()).isEqualTo(1L);
        assertThat(response.originHubCode()).isEqualTo("HCM-TD01");
        assertThat(response.destHubId()).isEqualTo(1L);
        assertThat(response.destHubCode()).isEqualTo("HCM-TD01");
        assertThat(response.volumetricWeightKg()).isEqualByComparingTo("0.00");
        assertThat(response.chargeableWeightKg()).isEqualByComparingTo("1.00");
        assertThat(response.options()).hasSize(1);

        ServiceOptionQuote quote = response.options().get(0);
        assertThat(quote.code()).isEqualTo("STANDARD");
        assertThat(quote.shippingFee()).isEqualByComparingTo("20000");
        assertThat(quote.codFee()).isEqualByComparingTo("0");
        assertThat(quote.totalFee()).isEqualByComparingTo("20000");
        assertThat(quote.estimatedDays()).isEqualTo("3 - 5 ngày");
    }

    @Test
    @DisplayName("TC-PRICE-02: Inter-province STANDARD, actual 1.0kg, 30x20x15cm, COD 500000 -> volumetric 1.80, chargeable 2.00, shipping 40000, cod 5000, total 45000")
    void tcPrice02_interProvinceStandardWithVolumetricWeightAndCod() {
        when(hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc("TP. Hồ Chí Minh"))
                .thenReturn(List.of(hcmPostOffice, hcmSortingHub));
        when(hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc("Hà Nội"))
                .thenReturn(List.of(hnPostOffice));
        when(serviceTypeRepository.findById(1L))
                .thenReturn(Optional.of(standardService));

        EstimateRequest request = EstimateRequest.builder()
                .senderProvince("TP. Hồ Chí Minh")
                .receiverProvince("Hà Nội")
                .actualWeightKg(new BigDecimal("1.0"))
                .lengthCm(30)
                .widthCm(20)
                .heightCm(15)
                .codAmount(new BigDecimal("500000"))
                .serviceTypeId(1L)
                .build();

        EstimateResponse response = pricingService.estimate(request);

        assertThat(response.interProvince()).isTrue();
        assertThat(response.originHubCode()).isEqualTo("HCM-TD01");
        assertThat(response.originHubName()).isEqualTo("Bưu cục Thủ Đức");
        assertThat(response.destHubCode()).isEqualTo("HN-CG01");
        assertThat(response.destHubName()).isEqualTo("Bưu cục Cầu Giấy");
        assertThat(response.volumetricWeightKg()).isEqualByComparingTo("1.80");
        assertThat(response.chargeableWeightKg()).isEqualByComparingTo("2.00");
        assertThat(response.options()).hasSize(1);

        ServiceOptionQuote quote = response.options().get(0);
        assertThat(quote.code()).isEqualTo("STANDARD");
        assertThat(quote.shippingFee()).isEqualByComparingTo("40000");
        assertThat(quote.codFee()).isEqualByComparingTo("5000");
        assertThat(quote.totalFee()).isEqualByComparingTo("45000");
        assertThat(quote.estimatedDays()).isEqualTo("3 - 5 ngày");
    }

    @Test
    @DisplayName("Estimate without serviceTypeId returns all active service options (STANDARD, EXPRESS, SAMEDAY)")
    void shouldCalculateAllActiveServicesForInterProvinceOrder() {
        when(hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc("TP. Hồ Chí Minh"))
                .thenReturn(List.of(hcmPostOffice));
        when(hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc("Hà Nội"))
                .thenReturn(List.of(hnPostOffice));
        when(serviceTypeRepository.findByActiveTrueOrderByBaseFeeAsc())
                .thenReturn(List.of(standardService, expressService, samedayService));

        EstimateRequest request = EstimateRequest.builder()
                .senderProvince("TP. Hồ Chí Minh")
                .receiverProvince("Hà Nội")
                .actualWeightKg(new BigDecimal("1.2"))
                .lengthCm(30)
                .widthCm(20)
                .heightCm(15)
                .codAmount(new BigDecimal("500000"))
                .serviceTypeId(null)
                .build();

        EstimateResponse response = pricingService.estimate(request);

        assertThat(response.interProvince()).isTrue();
        assertThat(response.volumetricWeightKg()).isEqualByComparingTo("1.80");
        assertThat(response.chargeableWeightKg()).isEqualByComparingTo("2.00");
        assertThat(response.options()).hasSize(3);

        // STANDARD: 20000 + 2*5000 + 10000 = 40000, COD 1% = 5000 -> 45000
        ServiceOptionQuote standard = response.options().get(0);
        assertThat(standard.code()).isEqualTo("STANDARD");
        assertThat(standard.shippingFee()).isEqualByComparingTo("40000");
        assertThat(standard.codFee()).isEqualByComparingTo("5000");
        assertThat(standard.totalFee()).isEqualByComparingTo("45000");

        // EXPRESS: 30000 + 2*7000 + 15000 = 59000, COD 1% = 5000 -> 64000
        ServiceOptionQuote express = response.options().get(1);
        assertThat(express.code()).isEqualTo("EXPRESS");
        assertThat(express.shippingFee()).isEqualByComparingTo("59000");
        assertThat(express.codFee()).isEqualByComparingTo("5000");
        assertThat(express.totalFee()).isEqualByComparingTo("64000");
        assertThat(express.estimatedDays()).isEqualTo("1 - 2 ngày");

        // SAMEDAY: 50000 + 2*10000 + 30000 = 100000, COD 1.5% = 7500 -> 107500
        ServiceOptionQuote sameday = response.options().get(2);
        assertThat(sameday.code()).isEqualTo("SAMEDAY");
        assertThat(sameday.shippingFee()).isEqualByComparingTo("100000");
        assertThat(sameday.codFee()).isEqualByComparingTo("7500");
        assertThat(sameday.totalFee()).isEqualByComparingTo("107500");
        assertThat(sameday.estimatedDays()).isEqualTo("0 - 1 ngày");
    }

    @Test
    @DisplayName("EXPRESS and SAMEDAY individual quote calculations for intra-province and overweight inter-province")
    void shouldCalculateExpressAndSamedayQuotesAccurately() {
        // SAMEDAY intra-province, chargeable 1.00 kg, COD 300000 -> 50000 + 4500 = 54500
        ServiceOptionQuote samedayIntra = pricingService.calculateQuote(
                samedayService, new BigDecimal("1.00"), false, new BigDecimal("300000"));
        assertThat(samedayIntra.shippingFee()).isEqualByComparingTo("50000");
        assertThat(samedayIntra.codFee()).isEqualByComparingTo("4500");
        assertThat(samedayIntra.totalFee()).isEqualByComparingTo("54500");

        // EXPRESS intra-province, chargeable 2.50 kg (3 extra steps), COD 0 -> 30000 + 3*7000 = 51000
        ServiceOptionQuote expressIntra = pricingService.calculateQuote(
                expressService, new BigDecimal("2.50"), false, BigDecimal.ZERO);
        assertThat(expressIntra.shippingFee()).isEqualByComparingTo("51000");
        assertThat(expressIntra.codFee()).isEqualByComparingTo("0");
        assertThat(expressIntra.totalFee()).isEqualByComparingTo("51000");

        // SAMEDAY inter-province, chargeable 2.50 kg (3 extra steps), COD 1000000 -> 50000 + 30000 + 30000 = 110000, COD 15000 -> 125000
        ServiceOptionQuote samedayInter = pricingService.calculateQuote(
                samedayService, new BigDecimal("2.50"), true, new BigDecimal("1000000"));
        assertThat(samedayInter.shippingFee()).isEqualByComparingTo("110000");
        assertThat(samedayInter.codFee()).isEqualByComparingTo("15000");
        assertThat(samedayInter.totalFee()).isEqualByComparingTo("125000");
    }

    @Test
    @DisplayName("Chargeable weight rounding edge cases: 0.4->0.50, 0.8->1.00, 1.0->1.00, 1.2->1.50, 1.8->2.00, 2.01->2.50")
    void shouldRoundChargeableWeightUpToHalfKgSteps() {
        assertThat(pricingService.calculateChargeableWeight(new BigDecimal("0.4"), BigDecimal.ZERO))
                .isEqualByComparingTo("0.50");
        assertThat(pricingService.calculateChargeableWeight(new BigDecimal("0.5"), BigDecimal.ZERO))
                .isEqualByComparingTo("0.50");
        assertThat(pricingService.calculateChargeableWeight(new BigDecimal("0.8"), BigDecimal.ZERO))
                .isEqualByComparingTo("1.00");
        assertThat(pricingService.calculateChargeableWeight(new BigDecimal("1.0"), BigDecimal.ZERO))
                .isEqualByComparingTo("1.00");
        assertThat(pricingService.calculateChargeableWeight(new BigDecimal("1.2"), BigDecimal.ZERO))
                .isEqualByComparingTo("1.50");
        assertThat(pricingService.calculateChargeableWeight(new BigDecimal("1.0"), new BigDecimal("1.80")))
                .isEqualByComparingTo("2.00");
        assertThat(pricingService.calculateChargeableWeight(new BigDecimal("2.01"), BigDecimal.ZERO))
                .isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("Volumetric weight returns 0.00 when any dimension is null or non-positive")
    void shouldReturnZeroVolumetricWeightWhenDimensionsIncomplete() {
        assertThat(pricingService.calculateVolumetricWeight(null, 20, 15)).isEqualByComparingTo("0.00");
        assertThat(pricingService.calculateVolumetricWeight(30, 0, 15)).isEqualByComparingTo("0.00");
        assertThat(pricingService.calculateVolumetricWeight(30, 20, -5)).isEqualByComparingTo("0.00");
        assertThat(pricingService.calculateVolumetricWeight(30, 20, 15)).isEqualByComparingTo("1.80");
    }

    @Test
    @DisplayName("resolveHubForProvince prefers POST_OFFICE over SORTING_HUB and falls back to SORTING_HUB")
    void shouldPreferPostOfficeOverSortingHub() {
        when(hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc("TP. Hồ Chí Minh"))
                .thenReturn(List.of(hcmSortingHub, hcmPostOffice));
        when(hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc("Đà Nẵng"))
                .thenReturn(List.of(danangSortingHub));

        Hub resolvedHcm = pricingService.resolveHubForProvince("  TP. Hồ Chí Minh  ");
        Hub resolvedDanang = pricingService.resolveHubForProvince("Đà Nẵng");

        assertThat(resolvedHcm.getCode()).isEqualTo("HCM-TD01");
        assertThat(resolvedHcm.getType()).isEqualTo(HubType.POST_OFFICE);
        assertThat(resolvedDanang.getCode()).isEqualTo("DNG-HC01");
        assertThat(resolvedDanang.getType()).isEqualTo(HubType.SORTING_HUB);
    }

    @Test
    @DisplayName("Unsupported province throws BusinessException")
    void shouldThrowBusinessExceptionForUnsupportedProvince() {
        when(hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc("Cà Mau"))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> pricingService.resolveHubForProvince("Cà Mau"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chưa hỗ trợ mạng lưới bưu cục tại tỉnh/thành: Cà Mau");
    }

    @Test
    @DisplayName("Inactive serviceTypeId throws BusinessException during estimate")
    void shouldThrowBusinessExceptionWhenSelectedServiceIsInactive() {
        ServiceType inactiveService = ServiceType.builder()
                .id(99L)
                .code("LEGACY")
                .name("Gói cũ")
                .active(false)
                .build();

        when(hubRepository.findByProvinceIgnoreCaseAndActiveTrueOrderByTypeAscIdAsc("TP. Hồ Chí Minh"))
                .thenReturn(List.of(hcmPostOffice));
        when(serviceTypeRepository.findById(99L))
                .thenReturn(Optional.of(inactiveService));

        EstimateRequest request = EstimateRequest.builder()
                .senderProvince("TP. Hồ Chí Minh")
                .receiverProvince("TP. Hồ Chí Minh")
                .actualWeightKg(new BigDecimal("1.0"))
                .serviceTypeId(99L)
                .build();

        assertThatThrownBy(() -> pricingService.estimate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tạm ngưng hoạt động");
    }
}
