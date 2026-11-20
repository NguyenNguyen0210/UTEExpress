package com.uteexpress.service;

import com.uteexpress.dto.customer.CreateShipmentRequest;
import com.uteexpress.dto.customer.CustomerDashboardSummary;
import com.uteexpress.dto.pricing.EstimateRequest;
import com.uteexpress.dto.pricing.EstimateResponse;
import com.uteexpress.dto.pricing.ServiceOptionQuote;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.CodStatus;
import com.uteexpress.enums.HubType;
import com.uteexpress.enums.InspectionPolicy;
import com.uteexpress.enums.PayerType;
import com.uteexpress.enums.PaymentMethod;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.enums.UserStatus;
import com.uteexpress.repository.ServiceTypeRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.ShipmentTrackingLogRepository;
import com.uteexpress.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentTrackingLogRepository trackingLogRepository;

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PricingService pricingService;

    @Mock
    private ShipmentService shipmentService;

    @InjectMocks
    private CustomerShipmentService customerShipmentService;

    private User customerA;
    private User customerB;
    private Hub hubHcm;
    private Hub hubHn;
    private ServiceType expressService;
    private EstimateResponse defaultEstimate;

    @BeforeEach
    void setUp() {
        customerA = User.builder()
                .id(10L)
                .email("shop@uteexpress.vn")
                .phone("0901234567")
                .fullName("Shop Hoàng Nam Tech")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        customerB = User.builder()
                .id(11L)
                .email("other@uteexpress.vn")
                .phone("0909998888")
                .fullName("Shop Khác")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        hubHcm = Hub.builder()
                .id(1L)
                .code("HCM-TD01")
                .name("Bưu cục Thủ Đức")
                .type(HubType.POST_OFFICE)
                .province("TP. Hồ Chí Minh")
                .district("TP. Thủ Đức")
                .active(true)
                .build();

        hubHn = Hub.builder()
                .id(6L)
                .code("HN-CG01")
                .name("Bưu cục Cầu Giấy")
                .type(HubType.POST_OFFICE)
                .province("Hà Nội")
                .district("Quận Cầu Giấy")
                .active(true)
                .build();

        expressService = ServiceType.builder()
                .id(2L)
                .code("EXPRESS")
                .name("Nhanh")
                .baseFee(new BigDecimal("30000"))
                .baseWeightKg(new BigDecimal("1.00"))
                .extraFeePerHalfKg(new BigDecimal("7000"))
                .interProvinceSurcharge(new BigDecimal("15000"))
                .codFeePercent(new BigDecimal("1.00"))
                .estimatedDaysMin(1)
                .estimatedDaysMax(2)
                .active(true)
                .build();

        ServiceOptionQuote quote = new ServiceOptionQuote(
                2L,
                "EXPRESS",
                "Nhanh",
                "Giao hàng nhanh",
                new BigDecimal("59000"),
                new BigDecimal("5000"),
                new BigDecimal("64000"),
                "1 - 2 ngày"
        );
        defaultEstimate = new EstimateResponse(
                true,
                hubHcm.getId(),
                hubHcm.getCode(),
                hubHcm.getName(),
                hubHn.getId(),
                hubHn.getCode(),
                hubHn.getName(),
                new BigDecimal("1.80"),
                new BigDecimal("2.00"),
                List.of(quote)
        );

        lenient().when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(100L);
            }
            return s;
        });
    }

    private CreateShipmentRequest buildBaseRequest(PayerType payerType, PaymentMethod paymentMethod, BigDecimal codAmount) {
        return CreateShipmentRequest.builder()
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
                .itemName("Tai nghe Bluetooth Sony WH-1000XM5")
                .actualWeightKg(new BigDecimal("1.2"))
                .lengthCm(30)
                .widthCm(20)
                .heightCm(15)
                .isFragile(true)
                .inspectionPolicy(InspectionPolicy.ALLOW_VIEW_NO_TRY)
                .note("Gọi trước khi giao 15 phút")
                .serviceTypeId(2L)
                .codAmount(codAmount)
                .payerType(payerType)
                .paymentMethod(paymentMethod)
                .build();
    }

    private void stubCommonCreateDependencies() {
        when(userRepository.findById(customerA.getId())).thenReturn(Optional.of(customerA));
        when(pricingService.estimate(any(EstimateRequest.class))).thenReturn(defaultEstimate);
        when(pricingService.resolveHubForProvince("TP. Hồ Chí Minh")).thenReturn(hubHcm);
        when(pricingService.resolveHubForProvince("Hà Nội")).thenReturn(hubHn);
        when(serviceTypeRepository.findById(2L)).thenReturn(Optional.of(expressService));
        when(shipmentRepository.existsByTrackingCodeIgnoreCase(anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("createShipment with SENDER + CASH creates CREATED shipment, valid UTE tracking code, and records tracking log")
    void createShipment_senderCash_createsCreatedAndRecordsTrackingLog() {
        stubCommonCreateDependencies();
        CreateShipmentRequest req = buildBaseRequest(PayerType.SENDER, PaymentMethod.CASH, new BigDecimal("500000"));

        Shipment saved = customerShipmentService.createShipment(customerA.getId(), req);

        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getTrackingCode()).matches("^UTE\\d{6}[A-Z0-9]{6}$");
        assertThat(saved.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(saved.getPayerType()).isEqualTo(PayerType.SENDER);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(saved.getCodStatus()).isEqualTo(CodStatus.PENDING);
        assertThat(saved.getOriginHub()).isEqualTo(hubHcm);
        assertThat(saved.getDestHub()).isEqualTo(hubHn);
        assertThat(saved.getCurrentHub()).isEqualTo(hubHcm);
        assertThat(saved.getChargeableWeightKg()).isEqualByComparingTo("2.00");
        assertThat(saved.getShippingFee()).isEqualByComparingTo("59000");
        assertThat(saved.getCodFee()).isEqualByComparingTo("5000");
        assertThat(saved.getTotalFee()).isEqualByComparingTo("64000");
        assertThat(saved.getIsFragile()).isTrue();
        assertThat(saved.getFailedDeliveryCount()).isZero();

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        verify(shipmentService, times(1)).recordTrackingLog(
                eq(saved),
                eq(ShipmentStatus.CREATED),
                eq(hubHcm),
                eq(customerA),
                titleCaptor.capture(),
                descCaptor.capture()
        );
        assertThat(titleCaptor.getValue()).isNotBlank();
        assertThat(descCaptor.getValue()).contains(saved.getTrackingCode());
    }

    @Test
    @DisplayName("createShipment with SENDER + VNPAY creates PENDING_PAYMENT shipment and records log")
    void createShipment_senderVnpay_createsPendingPayment() {
        stubCommonCreateDependencies();
        CreateShipmentRequest req = buildBaseRequest(PayerType.SENDER, PaymentMethod.VNPAY, BigDecimal.ZERO);

        Shipment saved = customerShipmentService.createShipment(customerA.getId(), req);

        assertThat(saved.getStatus()).isEqualTo(ShipmentStatus.PENDING_PAYMENT);
        assertThat(saved.getPayerType()).isEqualTo(PayerType.SENDER);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(saved.getCodStatus()).isEqualTo(CodStatus.NONE);

        verify(shipmentService, times(1)).recordTrackingLog(
                eq(saved),
                eq(ShipmentStatus.PENDING_PAYMENT),
                eq(hubHcm),
                eq(customerA),
                anyString(),
                anyString()
        );
    }

    @Test
    @DisplayName("BR-04: createShipment with RECEIVER + VNPAY forces CASH, creates CREATED, and sets codStatus = PENDING")
    void createShipment_receiverVnpay_forcesCashAndPendingCod() {
        stubCommonCreateDependencies();
        CreateShipmentRequest req = buildBaseRequest(PayerType.RECEIVER, PaymentMethod.VNPAY, BigDecimal.ZERO);

        Shipment saved = customerShipmentService.createShipment(customerA.getId(), req);

        assertThat(saved.getPayerType()).isEqualTo(PayerType.RECEIVER);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(saved.getCodStatus()).isEqualTo(CodStatus.PENDING);

        verify(shipmentService, times(1)).recordTrackingLog(
                eq(saved),
                eq(ShipmentStatus.CREATED),
                eq(hubHcm),
                eq(customerA),
                anyString(),
                anyString()
        );
    }

    @Test
    @DisplayName("TC-AUTH-03: getCustomerShipmentDetail throws AccessDeniedException when customer B accesses customer A's shipment")
    void tcAuth03_getCustomerShipmentDetail_otherCustomerThrowsAccessDenied() {
        Shipment customerAShipment = Shipment.builder()
                .id(55L)
                .trackingCode("UTE261120A8K4P2")
                .customer(customerA)
                .status(ShipmentStatus.CREATED)
                .build();

        when(shipmentRepository.findById(55L)).thenReturn(Optional.of(customerAShipment));

        // Owner succeeds
        Shipment detail = customerShipmentService.getCustomerShipmentDetail(customerA.getId(), 55L);
        assertThat(detail.getId()).isEqualTo(55L);

        // Customer B is blocked (anti-IDOR)
        assertThatThrownBy(() -> customerShipmentService.getCustomerShipmentDetail(customerB.getId(), 55L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("không có quyền");
    }

    @Test
    @DisplayName("generateTrackingCode retries on collision and avoids ambiguous characters 0, O, I, 1")
    void generateTrackingCode_retriesOnCollisionAndMatchesFormat() {
        when(shipmentRepository.existsByTrackingCodeIgnoreCase(anyString()))
                .thenReturn(true)
                .thenReturn(false);

        String code = customerShipmentService.generateTrackingCode();

        assertThat(code).matches("^UTE\\d{6}[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$");
        verify(shipmentRepository, times(2)).existsByTrackingCodeIgnoreCase(anyString());
    }

    @Test
    @DisplayName("getDashboardSummary computes KPI counts and sums pending COD for DELIVERED HELD_BY_SHIPPER / COLLECTED_BY_HUB")
    @SuppressWarnings("unchecked")
    void getDashboardSummary_computesCountsAndPendingCodAmount() {
        Shipment s1 = Shipment.builder().id(1L).codAmount(new BigDecimal("600000")).codStatus(CodStatus.HELD_BY_SHIPPER).build();
        Shipment s2 = Shipment.builder().id(2L).codAmount(new BigDecimal("400000")).codStatus(CodStatus.COLLECTED_BY_HUB).build();

        when(shipmentRepository.countByCustomerId(customerA.getId())).thenReturn(5L);
        when(shipmentRepository.countByCustomerIdAndStatus(customerA.getId(), ShipmentStatus.PENDING_PAYMENT)).thenReturn(1L);
        when(shipmentRepository.countByCustomerIdAndStatus(customerA.getId(), ShipmentStatus.DELIVERED)).thenReturn(2L);
        when(shipmentRepository.countByCustomerIdAndStatus(customerA.getId(), ShipmentStatus.CANCELLED)).thenReturn(0L);
        when(shipmentRepository.countByCustomerIdAndStatusIn(eq(customerA.getId()), anyCollection()))
                .thenReturn(2L)
                .thenReturn(0L);
        when(shipmentRepository.findAll(any(Specification.class))).thenReturn(List.of(s1, s2));
        when(shipmentRepository.findTop10ByCustomerIdOrderByCreatedAtDesc(customerA.getId())).thenReturn(List.of(s1, s2));

        CustomerDashboardSummary summary = customerShipmentService.getDashboardSummary(customerA.getId());

        assertThat(summary.totalShipments()).isEqualTo(5L);
        assertThat(summary.pendingPaymentCount()).isEqualTo(1L);
        assertThat(summary.inProgressCount()).isEqualTo(2L);
        assertThat(summary.deliveredCount()).isEqualTo(2L);
        assertThat(summary.failedOrReturnedCount()).isZero();
        assertThat(summary.cancelledCount()).isZero();
        assertThat(summary.pendingCodAmount()).isEqualByComparingTo("1000000");
        assertThat(summary.recentShipments()).hasSize(2);
    }

    @Test
    @DisplayName("getTabCounts returns counts for all 8 customer shipment tabs")
    void getTabCounts_returnsAll8Tabs() {
        when(shipmentRepository.countByCustomerId(customerA.getId())).thenReturn(10L);
        when(shipmentRepository.countByCustomerIdAndStatus(eq(customerA.getId()), any(ShipmentStatus.class))).thenReturn(1L);
        when(shipmentRepository.countByCustomerIdAndStatusIn(eq(customerA.getId()), anyCollection())).thenReturn(2L);

        Map<String, Long> tabCounts = customerShipmentService.getTabCounts(customerA.getId());

        assertThat(tabCounts).containsOnlyKeys(
                "all",
                "pending_payment",
                "waiting_pickup",
                "in_transit",
                "delivering",
                "delivered",
                "failed_returned",
                "cancelled"
        );
        assertThat(tabCounts.get("all")).isEqualTo(10L);
    }
}
