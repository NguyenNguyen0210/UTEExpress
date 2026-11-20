package com.uteexpress.service;

import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.entity.User;
import com.uteexpress.enums.CodStatus;
import com.uteexpress.enums.HubType;
import com.uteexpress.enums.PayerType;
import com.uteexpress.enums.PaymentMethod;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.enums.UserStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.ShipmentTrackingLogRepository;
import com.uteexpress.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentStateTransitionTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentTrackingLogRepository trackingLogRepository;

    @Mock
    private UserRepository userRepository;

    private ShipmentStateMachine stateMachine;
    private ShipmentService shipmentService;

    private Hub hubHcm;
    private Hub hubHn;
    private User admin;
    private User customer;
    private User otherCustomer;
    private User staffHcm;
    private User staffHn;
    private User shipperHcm;
    private User shipperHn;

    @BeforeEach
    void setUp() {
        stateMachine = new ShipmentStateMachine();
        shipmentService = new ShipmentService(shipmentRepository, trackingLogRepository, userRepository, stateMachine);

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
                .id(2L)
                .code("HN-CG01")
                .name("Bưu cục Cầu Giấy")
                .type(HubType.POST_OFFICE)
                .province("Hà Nội")
                .district("Quận Cầu Giấy")
                .active(true)
                .build();

        admin = User.builder()
                .id(1L)
                .email("admin@uteexpress.vn")
                .fullName("Admin UTEExpress")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        customer = User.builder()
                .id(10L)
                .email("shop@uteexpress.vn")
                .fullName("Shop Hoàng Nam")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        otherCustomer = User.builder()
                .id(11L)
                .email("other@uteexpress.vn")
                .fullName("Shop Khác")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        staffHcm = User.builder()
                .id(20L)
                .email("staff.hcm@uteexpress.vn")
                .fullName("Thu Hà")
                .role(Role.STAFF)
                .status(UserStatus.ACTIVE)
                .hub(hubHcm)
                .build();

        staffHn = User.builder()
                .id(21L)
                .email("staff.hn@uteexpress.vn")
                .fullName("Minh Tuấn")
                .role(Role.STAFF)
                .status(UserStatus.ACTIVE)
                .hub(hubHn)
                .build();

        shipperHcm = User.builder()
                .id(30L)
                .email("shipper.hcm@uteexpress.vn")
                .phone("0901000004")
                .fullName("Văn Hùng")
                .role(Role.SHIPPER)
                .status(UserStatus.ACTIVE)
                .hub(hubHcm)
                .build();

        shipperHn = User.builder()
                .id(31L)
                .email("shipper.hn@uteexpress.vn")
                .phone("0901000005")
                .fullName("Đức Anh")
                .role(Role.SHIPPER)
                .status(UserStatus.ACTIVE)
                .hub(hubHn)
                .build();

        lenient().when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(trackingLogRepository.save(any(ShipmentTrackingLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Shipment buildShipment(Long id, Hub origin, Hub dest, ShipmentStatus status) {
        return Shipment.builder()
                .id(id)
                .trackingCode("UTE261120TEST01")
                .customer(customer)
                .originHub(origin)
                .destHub(dest)
                .currentHub(origin)
                .status(status)
                .codAmount(new BigDecimal("500000"))
                .shippingFee(new BigDecimal("40000"))
                .codFee(new BigDecimal("5000"))
                .totalFee(new BigDecimal("45000"))
                .payerType(PayerType.SENDER)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.PAID)
                .codStatus(CodStatus.PENDING)
                .failedDeliveryCount(0)
                .build();
    }

    @Test
    @DisplayName("TC-LIFE-01: Full inter-province happy path records tracking logs and sets COD HELD_BY_SHIPPER")
    void tcLife01_fullInterProvinceHappyPath() {
        Shipment shipment = buildShipment(100L, hubHcm, hubHn, ShipmentStatus.CREATED);

        when(shipmentRepository.findById(100L)).thenReturn(Optional.of(shipment));
        when(userRepository.findById(shipperHcm.getId())).thenReturn(Optional.of(shipperHcm));
        when(userRepository.findById(shipperHn.getId())).thenReturn(Optional.of(shipperHn));

        // 1. CREATED -> ASSIGNED_PICKUP
        shipmentService.assignPickupShipper(100L, shipperHcm.getId(), staffHcm);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.ASSIGNED_PICKUP);
        assertThat(shipment.getPickupShipper()).isEqualTo(shipperHcm);

        // 2. ASSIGNED_PICKUP -> PICKED_UP
        shipmentService.markPickedUp(100L, shipperHcm, "Đã nhận tại kho shop");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);

        // 3. PICKED_UP -> IN_ORIGIN_HUB
        shipmentService.arriveAtOriginHub(100L, staffHcm, null);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_ORIGIN_HUB);
        assertThat(shipment.getCurrentHub()).isEqualTo(hubHcm);

        // 4. IN_ORIGIN_HUB -> IN_TRANSIT
        shipmentService.dispatchTransit(100L, staffHcm, "Xe tải tuyến Bắc - Nam");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);

        // 5. IN_TRANSIT -> IN_DEST_HUB
        shipmentService.arriveAtDestHub(100L, staffHn, null);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_DEST_HUB);
        assertThat(shipment.getCurrentHub()).isEqualTo(hubHn);

        // 6. IN_DEST_HUB -> OUT_FOR_DELIVERY
        shipmentService.assignDeliveryShipper(100L, shipperHn.getId(), staffHn);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(shipment.getDeliveryShipper()).isEqualTo(shipperHn);

        // 7. OUT_FOR_DELIVERY -> DELIVERED
        shipmentService.markDelivered(100L, shipperHn, "pod/abc-123.jpg", "Khách đã ký nhận");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.getPodImageKey()).isEqualTo("pod/abc-123.jpg");
        assertThat(shipment.getDeliveredAt()).isNotNull();
        assertThat(shipment.getCodStatus()).isEqualTo(CodStatus.HELD_BY_SHIPPER);

        ArgumentCaptor<ShipmentTrackingLog> logCaptor = ArgumentCaptor.forClass(ShipmentTrackingLog.class);
        verify(trackingLogRepository, times(7)).save(logCaptor.capture());

        List<ShipmentTrackingLog> logs = logCaptor.getAllValues();
        assertThat(logs).extracting(ShipmentTrackingLog::getStatus).containsExactly(
                ShipmentStatus.ASSIGNED_PICKUP,
                ShipmentStatus.PICKED_UP,
                ShipmentStatus.IN_ORIGIN_HUB,
                ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.IN_DEST_HUB,
                ShipmentStatus.OUT_FOR_DELIVERY,
                ShipmentStatus.DELIVERED
        );
        assertThat(logs.get(0).getHub()).isEqualTo(hubHcm);
        assertThat(logs.get(0).getActor()).isEqualTo(staffHcm);
        assertThat(logs.get(4).getHub()).isEqualTo(hubHn);
        assertThat(logs.get(4).getActor()).isEqualTo(staffHn);
        assertThat(logs.get(6).getHub()).isEqualTo(hubHn);
        assertThat(logs.get(6).getActor()).isEqualTo(shipperHn);
    }

    @Test
    @DisplayName("TC-LIFE-02: Intra-hub allows IN_ORIGIN_HUB -> IN_DEST_HUB directly and rejects IN_TRANSIT")
    void tcLife02_intraHubRouteRules() {
        Shipment intraHubShipment = buildShipment(101L, hubHcm, hubHcm, ShipmentStatus.IN_ORIGIN_HUB);
        when(shipmentRepository.findById(101L)).thenReturn(Optional.of(intraHubShipment));

        // Intra-hub cannot go to IN_TRANSIT
        assertThatThrownBy(() -> shipmentService.dispatchTransit(101L, staffHcm, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Đơn nội vùng không chuyển qua trung chuyển liên tỉnh, hãy chuyển thẳng sẵn sàng giao");

        // Intra-hub goes directly IN_ORIGIN_HUB -> IN_DEST_HUB
        Shipment updated = shipmentService.arriveAtDestHub(101L, staffHcm, "Nội vùng TP. Thủ Đức");
        assertThat(updated.getStatus()).isEqualTo(ShipmentStatus.IN_DEST_HUB);
        assertThat(updated.getCurrentHub()).isEqualTo(hubHcm);
        verify(trackingLogRepository, times(1)).save(any(ShipmentTrackingLog.class));
    }

    @Test
    @DisplayName("Inter-province shipment cannot bypass IN_TRANSIT from IN_ORIGIN_HUB directly to IN_DEST_HUB")
    void interProvinceCannotSkipTransit() {
        Shipment interHubShipment = buildShipment(102L, hubHcm, hubHn, ShipmentStatus.IN_ORIGIN_HUB);
        when(shipmentRepository.findById(102L)).thenReturn(Optional.of(interHubShipment));

        assertThatThrownBy(() -> shipmentService.arriveAtDestHub(102L, staffHn, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Đơn liên tỉnh bắt buộc xuất kho trung chuyển trước khi nhập bưu cục nhận");
    }

    @Test
    @DisplayName("Jumping states (CREATED -> DELIVERED, PICKED_UP -> OUT_FOR_DELIVERY, terminal states) rejected")
    void jumpingStatesShouldThrowBusinessException() {
        Shipment createdShipment = buildShipment(103L, hubHcm, hubHn, ShipmentStatus.CREATED);
        assertThat(stateMachine.canTransition(ShipmentStatus.CREATED, ShipmentStatus.DELIVERED)).isFalse();
        assertThatThrownBy(() -> stateMachine.validateTransition(createdShipment, ShipmentStatus.DELIVERED))
                .isInstanceOf(BusinessException.class);

        Shipment pickedUpShipment = buildShipment(104L, hubHcm, hubHn, ShipmentStatus.PICKED_UP);
        assertThat(stateMachine.canTransition(ShipmentStatus.PICKED_UP, ShipmentStatus.OUT_FOR_DELIVERY)).isFalse();
        assertThatThrownBy(() -> stateMachine.validateTransition(pickedUpShipment, ShipmentStatus.OUT_FOR_DELIVERY))
                .isInstanceOf(BusinessException.class);

        for (ShipmentStatus terminal : List.of(ShipmentStatus.DELIVERED, ShipmentStatus.RETURNED, ShipmentStatus.CANCELLED)) {
            Shipment terminalShipment = buildShipment(105L, hubHcm, hubHn, terminal);
            assertThat(stateMachine.getAllowedTransitions(terminal)).isEmpty();
            assertThatThrownBy(() -> stateMachine.validateTransition(terminalShipment, ShipmentStatus.CREATED))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("TC-LIFE-03: Staff from HN-CG01 attempting to assign pickup for HCM-TD01 order throws AccessDeniedException")
    void tcLife03_staffWrongHubThrowsAccessDenied() {
        Shipment hcmOrder = buildShipment(106L, hubHcm, hubHcm, ShipmentStatus.CREATED);
        when(shipmentRepository.findById(106L)).thenReturn(Optional.of(hcmOrder));

        assertThatThrownBy(() -> shipmentService.assignPickupShipper(106L, shipperHcm.getId(), staffHn))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Nhân viên chỉ được thao tác vận đơn thuộc bưu cục của mình");

        verify(shipmentRepository, never()).save(any());
        verify(trackingLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-LIFE-04: Customer cancelling CREATED PAID shipment transitions to CANCELLED and REFUND_PENDING")
    void tcLife04_customerCancelPaidOrderSetsRefundPending() {
        Shipment paidOrder = buildShipment(107L, hubHcm, hubHn, ShipmentStatus.CREATED);
        paidOrder.setPaymentStatus(PaymentStatus.PAID);
        when(shipmentRepository.findById(107L)).thenReturn(Optional.of(paidOrder));

        Shipment cancelled = shipmentService.cancelShipment(107L, customer, "Khách đổi ý đặt đơn khác");

        assertThat(cancelled.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        ArgumentCaptor<ShipmentTrackingLog> captor = ArgumentCaptor.forClass(ShipmentTrackingLog.class);
        verify(trackingLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(captor.getValue().getActor()).isEqualTo(customer);
    }

    @Test
    @DisplayName("TC-LIFE-05: Customer attempting to cancel PICKED_UP shipment throws BusinessException")
    void tcLife05_customerCancelAfterPickedUpThrowsBusinessException() {
        Shipment pickedUpOrder = buildShipment(108L, hubHcm, hubHn, ShipmentStatus.PICKED_UP);
        when(shipmentRepository.findById(108L)).thenReturn(Optional.of(pickedUpOrder));

        assertThat(stateMachine.isCancelable(ShipmentStatus.PICKED_UP)).isFalse();
        assertThatThrownBy(() -> shipmentService.cancelShipment(108L, customer, "Muốn hủy đơn"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể hủy vận đơn sau khi shipper đã lấy hàng");

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-LIFE-06: Failing delivery 3 times blocks 4th redelivery; RETURNING -> RETURNED succeeds")
    void tcLife06_threeFailedAttemptsBlocksRedeliveryAndAllowsReturn() {
        Shipment shipment = buildShipment(109L, hubHcm, hubHn, ShipmentStatus.IN_DEST_HUB);
        shipment.setCurrentHub(hubHn);

        when(shipmentRepository.findById(109L)).thenReturn(Optional.of(shipment));
        when(userRepository.findById(shipperHn.getId())).thenReturn(Optional.of(shipperHn));

        // Attempt 1
        shipmentService.assignDeliveryShipper(109L, shipperHn.getId(), staffHn);
        shipmentService.markDeliveryFailed(109L, shipperHn, "Khách không nghe máy lần 1");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERY_FAILED);
        assertThat(shipment.getFailedDeliveryCount()).isEqualTo(1);

        // Attempt 2
        shipmentService.assignDeliveryShipper(109L, shipperHn.getId(), staffHn);
        shipmentService.markDeliveryFailed(109L, shipperHn, "Khách hẹn giao lại lần 2");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERY_FAILED);
        assertThat(shipment.getFailedDeliveryCount()).isEqualTo(2);

        // Attempt 3
        shipmentService.assignDeliveryShipper(109L, shipperHn.getId(), staffHn);
        shipmentService.markDeliveryFailed(109L, shipperHn, "Khách từ chối nhận hàng lần 3");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERY_FAILED);
        assertThat(shipment.getFailedDeliveryCount()).isEqualTo(3);

        // Attempt 4 -> Blocked by BR-08
        assertThatThrownBy(() -> shipmentService.assignDeliveryShipper(109L, shipperHn.getId(), staffHn))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Vận đơn đã giao thất bại đủ 3 lần, không thể giao lại mà phải kích hoạt hoàn hàng");

        // Trigger return: DELIVERY_FAILED -> RETURNING -> RETURNED
        shipmentService.startReturn(109L, staffHn, "Giao thất bại đủ 3 lần");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.RETURNING);

        shipmentService.completeReturn(109L, staffHcm, "Shop đã nhận lại kiện hàng");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.RETURNED);
        assertThat(shipment.getCurrentHub()).isEqualTo(hubHcm);
    }

    @Test
    @DisplayName("TC-MEDIA-01: markDelivered with blank or null podImageKey throws BusinessException")
    void tcMedia01_markDeliveredWithoutPodThrowsBusinessException() {
        Shipment shipment = buildShipment(110L, hubHcm, hubHn, ShipmentStatus.OUT_FOR_DELIVERY);
        shipment.setDeliveryShipper(shipperHn);
        when(shipmentRepository.findById(110L)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.markDelivered(110L, shipperHn, "   ", "Giao xong"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("POD");

        assertThatThrownBy(() -> shipmentService.markDelivered(110L, shipperHn, null, "Giao xong"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("POD");

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("COD status on DELIVERED and calculateReceiverCollectAmount rules")
    void codStatusAndReceiverCollectAmountCalculations() {
        // Case 1: No COD, SENDER paid -> CodStatus.NONE, collect = 0
        Shipment noCodSenderPaid = buildShipment(111L, hubHcm, hubHn, ShipmentStatus.OUT_FOR_DELIVERY);
        noCodSenderPaid.setDeliveryShipper(shipperHn);
        noCodSenderPaid.setCodAmount(BigDecimal.ZERO);
        noCodSenderPaid.setPayerType(PayerType.SENDER);
        noCodSenderPaid.setPaymentStatus(PaymentStatus.PAID);
        when(shipmentRepository.findById(111L)).thenReturn(Optional.of(noCodSenderPaid));

        shipmentService.markDelivered(111L, shipperHn, "pod/no-cod.jpg", null);
        assertThat(noCodSenderPaid.getCodStatus()).isEqualTo(CodStatus.NONE);
        assertThat(shipmentService.calculateReceiverCollectAmount(noCodSenderPaid)).isEqualByComparingTo("0");

        // Case 2: No COD, RECEIVER UNPAID -> CodStatus.HELD_BY_SHIPPER, collect = totalFee
        Shipment receiverPaysFeeOnly = buildShipment(112L, hubHcm, hubHn, ShipmentStatus.OUT_FOR_DELIVERY);
        receiverPaysFeeOnly.setDeliveryShipper(shipperHn);
        receiverPaysFeeOnly.setCodAmount(BigDecimal.ZERO);
        receiverPaysFeeOnly.setTotalFee(new BigDecimal("45000"));
        receiverPaysFeeOnly.setPayerType(PayerType.RECEIVER);
        receiverPaysFeeOnly.setPaymentStatus(PaymentStatus.UNPAID);
        when(shipmentRepository.findById(112L)).thenReturn(Optional.of(receiverPaysFeeOnly));

        shipmentService.markDelivered(112L, shipperHn, "pod/receiver-fee.jpg", null);
        assertThat(receiverPaysFeeOnly.getCodStatus()).isEqualTo(CodStatus.HELD_BY_SHIPPER);
        assertThat(shipmentService.calculateReceiverCollectAmount(receiverPaysFeeOnly)).isEqualByComparingTo("45000");

        // Case 3: COD 300k + RECEIVER UNPAID fee 45k -> collect = 345k
        receiverPaysFeeOnly.setCodAmount(new BigDecimal("300000"));
        assertThat(shipmentService.calculateReceiverCollectAmount(receiverPaysFeeOnly)).isEqualByComparingTo("345000");
    }

    @Test
    @DisplayName("Anti-IDOR access control: assertCanViewShipment and unauthorized cancel blocked")
    void antiIdorAccessControlChecks() {
        Shipment hcmOnlyShipment = buildShipment(113L, hubHcm, hubHcm, ShipmentStatus.ASSIGNED_PICKUP);
        hcmOnlyShipment.setPickupShipper(shipperHcm);

        // Allowed viewers
        shipmentService.assertCanViewShipment(hcmOnlyShipment, admin);
        shipmentService.assertCanViewShipment(hcmOnlyShipment, customer);
        shipmentService.assertCanViewShipment(hcmOnlyShipment, staffHcm);
        shipmentService.assertCanViewShipment(hcmOnlyShipment, shipperHcm);

        // Denied viewers
        assertThatThrownBy(() -> shipmentService.assertCanViewShipment(hcmOnlyShipment, otherCustomer))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Bạn không có quyền truy cập vận đơn này");
        assertThatThrownBy(() -> shipmentService.assertCanViewShipment(hcmOnlyShipment, staffHn))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Bạn không có quyền truy cập vận đơn này");
        assertThatThrownBy(() -> shipmentService.assertCanViewShipment(hcmOnlyShipment, shipperHn))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Bạn không có quyền truy cập vận đơn này");

        // Another customer cannot cancel someone else's shipment
        when(shipmentRepository.findById(113L)).thenReturn(Optional.of(hcmOnlyShipment));
        assertThatThrownBy(() -> shipmentService.cancelShipment(113L, otherCustomer, "Hủy trộm"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Validation errors: locked shipper, wrong-hub shipper, blank failure reason, unassigned shipper")
    void operationalValidationEdgeCases() {
        Shipment created = buildShipment(114L, hubHcm, hubHn, ShipmentStatus.CREATED);
        when(shipmentRepository.findById(114L)).thenReturn(Optional.of(created));

        User lockedShipper = User.builder()
                .id(32L)
                .fullName("Locked Shipper")
                .role(Role.SHIPPER)
                .status(UserStatus.LOCKED)
                .hub(hubHcm)
                .build();
        when(userRepository.findById(32L)).thenReturn(Optional.of(lockedShipper));
        assertThatThrownBy(() -> shipmentService.assignPickupShipper(114L, 32L, staffHcm))
                .isInstanceOf(BusinessException.class);

        when(userRepository.findById(shipperHn.getId())).thenReturn(Optional.of(shipperHn));
        assertThatThrownBy(() -> shipmentService.assignPickupShipper(114L, shipperHn.getId(), staffHcm))
                .isInstanceOf(BusinessException.class);

        Shipment outForDelivery = buildShipment(115L, hubHcm, hubHn, ShipmentStatus.OUT_FOR_DELIVERY);
        outForDelivery.setDeliveryShipper(shipperHn);
        when(shipmentRepository.findById(115L)).thenReturn(Optional.of(outForDelivery));

        assertThatThrownBy(() -> shipmentService.markDeliveryFailed(115L, shipperHcm, "Sai shipper"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> shipmentService.markDeliveryFailed(115L, shipperHn, "   "))
                .isInstanceOf(BusinessException.class);
    }
}
