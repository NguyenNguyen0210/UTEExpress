package com.uteexpress.service;

import com.uteexpress.entity.CodSettlement;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.*;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.repository.CodSettlementRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodSettlementServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private CodSettlementRepository codSettlementRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ShipmentService shipmentService;

    private CodSettlementService codSettlementService;

    private Hub hubHcm;
    private User admin;
    private User staffHcm;
    private User shop;

    @BeforeEach
    void setUp() {
        codSettlementService = new CodSettlementService(
                shipmentRepository,
                codSettlementRepository,
                userRepository,
                notificationService,
                shipmentService
        );

        hubHcm = Hub.builder().id(1L).code("HCM-TD01").name("Bưu cục Thủ Đức").build();
        admin = User.builder().id(1L).email("admin@uteexpress.vn").role(Role.ADMIN).status(UserStatus.ACTIVE).build();
        staffHcm = User.builder().id(2L).email("staff.hcm@uteexpress.vn").role(Role.STAFF).hub(hubHcm).status(UserStatus.ACTIVE).build();
        shop = User.builder()
                .id(6L)
                .email("shop@uteexpress.vn")
                .fullName("Shop Hoàng Nam")
                .role(Role.CUSTOMER)
                .bankName("Vietcombank")
                .bankAccountNumber("1023456789")
                .bankAccountHolder("HOANG VAN NAM")
                .build();
    }

    @Test
    void tcCod01_createSettlementComputesShippingFeeDeductionAndBankSnapshot() {
        // Order 1: COD 300,000, already PAID via VNPay -> deduction 0
        Shipment s1 = Shipment.builder()
                .id(101L)
                .trackingCode("UTE261120COD001")
                .customer(shop)
                .status(ShipmentStatus.DELIVERED)
                .codStatus(CodStatus.COLLECTED_BY_HUB)
                .codAmount(new BigDecimal("300000"))
                .totalFee(new BigDecimal("45000"))
                .payerType(PayerType.SENDER)
                .paymentStatus(PaymentStatus.PAID)
                .build();

        // Order 2: COD 200,000, SENDER CASH UNPAID fee 30,000 -> deduction 30,000
        Shipment s2 = Shipment.builder()
                .id(102L)
                .trackingCode("UTE261120COD002")
                .customer(shop)
                .status(ShipmentStatus.DELIVERED)
                .codStatus(CodStatus.COLLECTED_BY_HUB)
                .codAmount(new BigDecimal("200000"))
                .totalFee(new BigDecimal("30000"))
                .payerType(PayerType.SENDER)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();

        when(shipmentRepository.findByCustomerIdAndStatusAndCodStatusAndSettlementIsNullOrderByDeliveredAtAsc(
                6L, ShipmentStatus.DELIVERED, CodStatus.COLLECTED_BY_HUB))
                .thenReturn(List.of(s1, s2));
        when(userRepository.findById(6L)).thenReturn(Optional.of(shop));
        when(codSettlementRepository.findBySettlementCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(codSettlementRepository.save(any(CodSettlement.class))).thenAnswer(inv -> {
            CodSettlement cs = inv.getArgument(0);
            cs.setId(50L);
            return cs;
        });

        CodSettlement created = codSettlementService.createSettlement(6L, "Đối soát đợt 1", admin);

        assertThat(created.getSettlementCode()).startsWith("COD");
        assertThat(created.getTotalCodAmount()).isEqualByComparingTo("500000");
        assertThat(created.getTotalShippingDeduction()).isEqualByComparingTo("30000");
        assertThat(created.getNetAmount()).isEqualByComparingTo("470000");
        assertThat(created.getBankName()).isEqualTo("Vietcombank");
        assertThat(created.getBankAccountNumber()).isEqualTo("1023456789");
        assertThat(created.getBankAccountHolder()).isEqualTo("HOANG VAN NAM");
        assertThat(created.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(s1.getSettlement()).isEqualTo(created);
        assertThat(s2.getSettlement()).isEqualTo(created);
        verify(shipmentRepository).saveAll(List.of(s1, s2));
    }

    @Test
    void tcCod02_markSettledUpdatesShipmentsNotifiesCustomerAndBlocksDuplicateSettle() {
        CodSettlement settlement = CodSettlement.builder()
                .id(50L)
                .settlementCode("COD261120ABCD")
                .customer(shop)
                .totalCodAmount(new BigDecimal("500000"))
                .totalShippingDeduction(new BigDecimal("30000"))
                .netAmount(new BigDecimal("470000"))
                .status(SettlementStatus.PENDING)
                .build();

        Shipment s1 = Shipment.builder()
                .id(101L).customer(shop).status(ShipmentStatus.DELIVERED)
                .codStatus(CodStatus.COLLECTED_BY_HUB)
                .payerType(PayerType.SENDER).paymentStatus(PaymentStatus.PAID).build();
        Shipment s2 = Shipment.builder()
                .id(102L).customer(shop).status(ShipmentStatus.DELIVERED)
                .codStatus(CodStatus.COLLECTED_BY_HUB)
                .payerType(PayerType.SENDER).paymentStatus(PaymentStatus.UNPAID).build();

        when(codSettlementRepository.findById(50L)).thenReturn(Optional.of(settlement));
        when(codSettlementRepository.save(any(CodSettlement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipmentRepository.findBySettlementIdOrderByDeliveredAtAsc(50L)).thenReturn(List.of(s1, s2));

        CodSettlement settled = codSettlementService.markSettled(50L, "VCB-FT261120999", admin);

        assertThat(settled.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(settled.getTransactionRef()).isEqualTo("VCB-FT261120999");
        assertThat(settled.getSettledAt()).isNotNull();
        assertThat(s1.getCodStatus()).isEqualTo(CodStatus.SETTLED);
        assertThat(s2.getCodStatus()).isEqualTo(CodStatus.SETTLED);
        // SENDER + UNPAID shipping fee was deducted from COD -> now marked PAID
        assertThat(s2.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(notificationService).sendToUser(eq(shop), contains("COD"), contains("VCB-FT261120999"), eq("/customer/cod"));

        // Duplicate settle rejected
        assertThatThrownBy(() -> codSettlementService.markSettled(50L, "DUP-REF", admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được thanh toán");
    }

    @Test
    void staffCollectCodFromShipperUpdatesStatusAndEnforcesHubAntiIdor() {
        Shipment s = Shipment.builder()
                .id(200L)
                .trackingCode("UTE261120COLLECT")
                .destHub(hubHcm)
                .status(ShipmentStatus.DELIVERED)
                .codStatus(CodStatus.HELD_BY_SHIPPER)
                .build();

        when(shipmentRepository.findById(200L)).thenReturn(Optional.of(s));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = codSettlementService.collectCodFromShipper(List.of(200L), staffHcm);

        assertThat(count).isEqualTo(1);
        assertThat(s.getCodStatus()).isEqualTo(CodStatus.COLLECTED_BY_HUB);
        assertThat(s.getCodCollectedBy()).isEqualTo(staffHcm);
        assertThat(s.getCodCollectedAt()).isNotNull();
        verify(shipmentService).assertStaffOwnsHub(staffHcm, 1L);
    }

    @Test
    void staffFromOtherHubRejectedWhenCollectingCod() {
        Hub hubHn = Hub.builder().id(2L).code("HN-CG01").build();
        Shipment s = Shipment.builder()
                .id(201L).destHub(hubHn).status(ShipmentStatus.DELIVERED).codStatus(CodStatus.HELD_BY_SHIPPER).build();

        when(shipmentRepository.findById(201L)).thenReturn(Optional.of(s));
        doThrow(new AccessDeniedException("Khác bưu cục")).when(shipmentService).assertStaffOwnsHub(staffHcm, 2L);

        assertThatThrownBy(() -> codSettlementService.collectCodFromShipper(List.of(201L), staffHcm))
                .isInstanceOf(AccessDeniedException.class);
    }
}
