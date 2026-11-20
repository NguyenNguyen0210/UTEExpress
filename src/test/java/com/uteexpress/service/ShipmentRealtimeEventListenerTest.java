package com.uteexpress.service;

import com.uteexpress.dto.publicview.TrackingUpdateEvent;
import com.uteexpress.dto.ws.ShipmentStatusChangedEvent;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentRealtimeEventListenerTest {

    private static final Long SHIPMENT_ID = 12L;
    private static final String TRACKING_CODE = "UTE261120A8K4P2";
    private static final Long ORIGIN_HUB_ID = 1L;
    private static final Long DEST_HUB_ID = 6L;
    private static final Long CUSTOMER_ID = 10L;
    private static final Long PICKUP_SHIPPER_ID = 30L;
    private static final Long DELIVERY_SHIPPER_ID = 31L;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ShipmentRealtimeEventListener listener;

    private User customer;
    private User pickupShipper;
    private User deliveryShipper;
    private OffsetDateTime fixedTime;

    @BeforeEach
    void setUp() {
        fixedTime = OffsetDateTime.parse("2026-11-20T14:20:00+07:00");

        customer = User.builder()
                .id(CUSTOMER_ID)
                .email("shop@uteexpress.vn")
                .fullName("Shop Hoàng Nam")
                .role(Role.CUSTOMER)
                .build();

        pickupShipper = User.builder()
                .id(PICKUP_SHIPPER_ID)
                .email("shipper.hcm@uteexpress.vn")
                .fullName("Văn Hùng")
                .role(Role.SHIPPER)
                .build();

        deliveryShipper = User.builder()
                .id(DELIVERY_SHIPPER_ID)
                .email("shipper.hn@uteexpress.vn")
                .fullName("Đức Anh")
                .role(Role.SHIPPER)
                .build();
    }

    @Test
    @DisplayName("CREATED broadcasts /topic/tracking/{code} and notifies originHub staff")
    void createdBroadcastsTrackingAndNotifiesOriginHubStaff() {
        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                SHIPMENT_ID,
                TRACKING_CODE,
                ShipmentStatus.CREATED,
                ORIGIN_HUB_ID,
                DEST_HUB_ID,
                ORIGIN_HUB_ID,
                "Bưu cục Thủ Đức (HCM-TD01)",
                CUSTOMER_ID,
                null,
                null,
                "Đã tạo vận đơn",
                "Đơn hàng sẵn sàng phân công lấy hàng.",
                fixedTime
        );

        listener.onShipmentStatusChanged(event);

        ArgumentCaptor<TrackingUpdateEvent> payloadCaptor = ArgumentCaptor.forClass(TrackingUpdateEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tracking/" + TRACKING_CODE), payloadCaptor.capture());

        TrackingUpdateEvent payload = payloadCaptor.getValue();
        assertThat(payload.trackingCode()).isEqualTo(TRACKING_CODE);
        assertThat(payload.status()).isEqualTo("CREATED");
        assertThat(payload.statusLabel()).isEqualTo(ShipmentStatus.CREATED.getLabel());
        assertThat(payload.currentHubName()).isEqualTo("Bưu cục Thủ Đức (HCM-TD01)");
        assertThat(payload.logTitle()).isEqualTo("Đã tạo vận đơn");
        assertThat(payload.logDescription()).isEqualTo("Đơn hàng sẵn sàng phân công lấy hàng.");
        assertThat(payload.timestamp()).isEqualTo(fixedTime);

        verify(notificationService).sendToHubStaff(
                eq(ORIGIN_HUB_ID),
                eq("Đơn hàng mới cần điều phối"),
                eq("Vận đơn " + TRACKING_CODE + " vừa được tạo và sẵn sàng phân công lấy hàng."),
                eq("/staff/shipments/" + SHIPMENT_ID)
        );
        verify(notificationService, never()).sendToUser(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("ASSIGNED_PICKUP broadcasts tracking and notifies pickupShipper and customer")
    void assignedPickupNotifiesPickupShipperAndCustomer() {
        when(userRepository.findById(PICKUP_SHIPPER_ID)).thenReturn(Optional.of(pickupShipper));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                SHIPMENT_ID,
                TRACKING_CODE,
                ShipmentStatus.ASSIGNED_PICKUP,
                ORIGIN_HUB_ID,
                DEST_HUB_ID,
                ORIGIN_HUB_ID,
                "Bưu cục Thủ Đức (HCM-TD01)",
                CUSTOMER_ID,
                PICKUP_SHIPPER_ID,
                null,
                "Đã phân công Shipper lấy hàng",
                "Bưu cục Thủ Đức đã phân công Shipper Văn Hùng đến lấy hàng.",
                fixedTime
        );

        listener.onShipmentStatusChanged(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/tracking/" + TRACKING_CODE), any(TrackingUpdateEvent.class));
        verify(notificationService).sendToUser(
                eq(pickupShipper),
                anyString(),
                contains(TRACKING_CODE),
                eq("/shipper/tasks?tab=pickup")
        );
        verify(notificationService).sendToUser(
                eq(customer),
                anyString(),
                contains(TRACKING_CODE),
                eq("/customer/shipments/" + SHIPMENT_ID)
        );
        verify(notificationService, never()).sendToHubStaff(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("IN_TRANSIT broadcasts tracking and notifies destHub staff and customer")
    void inTransitNotifiesDestHubStaffAndCustomer() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                SHIPMENT_ID,
                TRACKING_CODE,
                ShipmentStatus.IN_TRANSIT,
                ORIGIN_HUB_ID,
                DEST_HUB_ID,
                ORIGIN_HUB_ID,
                "Bưu cục Thủ Đức (HCM-TD01)",
                CUSTOMER_ID,
                PICKUP_SHIPPER_ID,
                null,
                "Đã xuất kho trung chuyển liên tỉnh",
                "Đang luân chuyển từ HCM-TD01 đến HN-CG01.",
                fixedTime
        );

        listener.onShipmentStatusChanged(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/tracking/" + TRACKING_CODE), any(TrackingUpdateEvent.class));
        verify(notificationService).sendToHubStaff(
                eq(DEST_HUB_ID),
                eq("Hàng trung chuyển đang đến"),
                contains(TRACKING_CODE),
                eq("/staff/shipments/" + SHIPMENT_ID)
        );
        verify(notificationService).sendToUser(
                eq(customer),
                anyString(),
                contains(TRACKING_CODE),
                eq("/customer/shipments/" + SHIPMENT_ID)
        );
    }

    @Test
    @DisplayName("OUT_FOR_DELIVERY broadcasts tracking and notifies deliveryShipper and customer")
    void outForDeliveryNotifiesDeliveryShipperAndCustomer() {
        when(userRepository.findById(DELIVERY_SHIPPER_ID)).thenReturn(Optional.of(deliveryShipper));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                SHIPMENT_ID,
                TRACKING_CODE,
                ShipmentStatus.OUT_FOR_DELIVERY,
                ORIGIN_HUB_ID,
                DEST_HUB_ID,
                DEST_HUB_ID,
                "Bưu cục Cầu Giấy (HN-CG01)",
                CUSTOMER_ID,
                PICKUP_SHIPPER_ID,
                DELIVERY_SHIPPER_ID,
                "Shipper đang trên đường giao hàng",
                "Shipper Đức Anh đang đi giao hàng.",
                fixedTime
        );

        listener.onShipmentStatusChanged(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/tracking/" + TRACKING_CODE), any(TrackingUpdateEvent.class));
        verify(notificationService).sendToUser(
                eq(deliveryShipper),
                anyString(),
                contains(TRACKING_CODE),
                eq("/shipper/tasks?tab=delivery")
        );
        verify(notificationService).sendToUser(
                eq(customer),
                anyString(),
                contains(TRACKING_CODE),
                eq("/customer/shipments/" + SHIPMENT_ID)
        );
        verify(notificationService, never()).sendToHubStaff(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("DELIVERED broadcasts /topic/tracking/{code} and notifies customer")
    void deliveredBroadcastsTrackingAndNotifiesCustomer() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                SHIPMENT_ID,
                TRACKING_CODE,
                ShipmentStatus.DELIVERED,
                ORIGIN_HUB_ID,
                DEST_HUB_ID,
                DEST_HUB_ID,
                "Bưu cục Cầu Giấy (HN-CG01)",
                CUSTOMER_ID,
                PICKUP_SHIPPER_ID,
                DELIVERY_SHIPPER_ID,
                "Giao hàng thành công",
                "Người nhận đã ký nhận hàng.",
                fixedTime
        );

        listener.onShipmentStatusChanged(event);

        ArgumentCaptor<TrackingUpdateEvent> payloadCaptor = ArgumentCaptor.forClass(TrackingUpdateEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tracking/" + TRACKING_CODE), payloadCaptor.capture());

        TrackingUpdateEvent payload = payloadCaptor.getValue();
        assertThat(payload.trackingCode()).isEqualTo(TRACKING_CODE);
        assertThat(payload.status()).isEqualTo("DELIVERED");
        assertThat(payload.statusLabel()).isEqualTo(ShipmentStatus.DELIVERED.getLabel());
        assertThat(payload.currentHubName()).isEqualTo("Bưu cục Cầu Giấy (HN-CG01)");

        verify(notificationService).sendToUser(
                eq(customer),
                eq("Giao hàng thành công"),
                contains(TRACKING_CODE),
                eq("/customer/shipments/" + SHIPMENT_ID)
        );
        verify(notificationService, never()).sendToHubStaff(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("DELIVERY_FAILED notifies both destHub staff and customer")
    void deliveryFailedNotifiesDestHubStaffAndCustomer() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                SHIPMENT_ID,
                TRACKING_CODE,
                ShipmentStatus.DELIVERY_FAILED,
                ORIGIN_HUB_ID,
                DEST_HUB_ID,
                DEST_HUB_ID,
                "Bưu cục Cầu Giấy (HN-CG01)",
                CUSTOMER_ID,
                PICKUP_SHIPPER_ID,
                DELIVERY_SHIPPER_ID,
                "Giao hàng thất bại (Lần 1)",
                "Lý do: Khách không nghe máy",
                fixedTime
        );

        listener.onShipmentStatusChanged(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/tracking/" + TRACKING_CODE), any(TrackingUpdateEvent.class));
        verify(notificationService).sendToHubStaff(
                eq(DEST_HUB_ID),
                eq("Giao hàng thất bại"),
                contains("Khách không nghe máy"),
                eq("/staff/shipments/" + SHIPMENT_ID)
        );
        verify(notificationService).sendToUser(
                eq(customer),
                eq("Giao hàng thất bại"),
                contains("Khách không nghe máy"),
                eq("/customer/shipments/" + SHIPMENT_ID)
        );
    }

    @Test
    @DisplayName("PICKED_UP notifies customer and originHub staff; IN_DEST_HUB notifies destHub staff only when intra-hub")
    void pickedUpAndIntraHubDestHubNotifications() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        ShipmentStatusChangedEvent pickedUpEvent = new ShipmentStatusChangedEvent(
                SHIPMENT_ID,
                TRACKING_CODE,
                ShipmentStatus.PICKED_UP,
                ORIGIN_HUB_ID,
                DEST_HUB_ID,
                ORIGIN_HUB_ID,
                "Bưu cục Thủ Đức (HCM-TD01)",
                CUSTOMER_ID,
                PICKUP_SHIPPER_ID,
                null,
                "Shipper đã lấy hàng thành công",
                "Đã nhận kiện hàng từ người gửi.",
                fixedTime
        );

        listener.onShipmentStatusChanged(pickedUpEvent);

        verify(notificationService).sendToUser(eq(customer), anyString(), contains(TRACKING_CODE), eq("/customer/shipments/" + SHIPMENT_ID));
        verify(notificationService).sendToHubStaff(eq(ORIGIN_HUB_ID), anyString(), contains(TRACKING_CODE), eq("/staff/shipments/" + SHIPMENT_ID));

        // Intra-hub IN_DEST_HUB (originHubId == destHubId) notifies both customer and destHub staff
        ShipmentStatusChangedEvent intraHubDestEvent = new ShipmentStatusChangedEvent(
                SHIPMENT_ID,
                TRACKING_CODE,
                ShipmentStatus.IN_DEST_HUB,
                ORIGIN_HUB_ID,
                ORIGIN_HUB_ID,
                ORIGIN_HUB_ID,
                "Bưu cục Thủ Đức (HCM-TD01)",
                CUSTOMER_ID,
                PICKUP_SHIPPER_ID,
                null,
                "Hàng sẵn sàng phát tại Bưu cục Thủ Đức (HCM-TD01)",
                "Đơn nội vùng chuyển thẳng sẵn sàng giao.",
                fixedTime
        );

        listener.onShipmentStatusChanged(intraHubDestEvent);

        verify(notificationService).sendToHubStaff(
                eq(ORIGIN_HUB_ID),
                eq("Đơn nội vùng sẵn sàng phát"),
                contains(TRACKING_CODE),
                eq("/staff/shipments/" + SHIPMENT_ID)
        );
    }

    @Test
    @DisplayName("IN_ORIGIN_HUB, inter-province IN_DEST_HUB, RETURNING, RETURNED, and CANCELLED notify customer only")
    void customerOnlyLifecycleStatusesNotifyCustomerWithoutHubStaff() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        ShipmentStatus[] customerOnlyStatuses = {
                ShipmentStatus.IN_ORIGIN_HUB,
                ShipmentStatus.IN_DEST_HUB,
                ShipmentStatus.RETURNING,
                ShipmentStatus.RETURNED,
                ShipmentStatus.CANCELLED
        };

        for (ShipmentStatus status : customerOnlyStatuses) {
            ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                    SHIPMENT_ID,
                    TRACKING_CODE,
                    status,
                    ORIGIN_HUB_ID,
                    DEST_HUB_ID,
                    DEST_HUB_ID,
                    "Bưu cục Cầu Giấy (HN-CG01)",
                    CUSTOMER_ID,
                    PICKUP_SHIPPER_ID,
                    DELIVERY_SHIPPER_ID,
                    status.getLabel(),
                    "Cập nhật trạng thái " + status.name(),
                    fixedTime
            );
            listener.onShipmentStatusChanged(event);
        }

        verify(messagingTemplate, times(customerOnlyStatuses.length))
                .convertAndSend(eq("/topic/tracking/" + TRACKING_CODE), any(TrackingUpdateEvent.class));
        verify(notificationService, times(customerOnlyStatuses.length))
                .sendToUser(eq(customer), anyString(), contains(TRACKING_CODE), eq("/customer/shipments/" + SHIPMENT_ID));
        verify(notificationService, never()).sendToHubStaff(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("WebSocket broadcast error is caught and does not break bell notification dispatch")
    void wsBroadcastExceptionDoesNotBreakNotificationDispatch() {
        doThrow(new RuntimeException("Broker unavailable"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                SHIPMENT_ID,
                TRACKING_CODE,
                ShipmentStatus.CREATED,
                ORIGIN_HUB_ID,
                DEST_HUB_ID,
                ORIGIN_HUB_ID,
                "Bưu cục Thủ Đức (HCM-TD01)",
                CUSTOMER_ID,
                null,
                null,
                "Đã tạo vận đơn",
                "Sẵn sàng lấy hàng",
                fixedTime
        );

        assertThatCode(() -> listener.onShipmentStatusChanged(event)).doesNotThrowAnyException();

        verify(notificationService).sendToHubStaff(
                eq(ORIGIN_HUB_ID),
                eq("Đơn hàng mới cần điều phối"),
                contains(TRACKING_CODE),
                eq("/staff/shipments/" + SHIPMENT_ID)
        );
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("ShipmentStatusChangedEvent.from formats hub name with code and maps entity fields")
    void eventFactoryMapsEntitiesAndFormatsHubDisplay() {
        Hub originHub = Hub.builder().id(ORIGIN_HUB_ID).code("HCM-TD01").name("Bưu cục Thủ Đức").build();
        Hub destHub = Hub.builder().id(DEST_HUB_ID).code("HN-CG01").name("Bưu cục Cầu Giấy").build();

        Shipment shipment = Shipment.builder()
                .id(SHIPMENT_ID)
                .trackingCode(TRACKING_CODE)
                .status(ShipmentStatus.IN_DEST_HUB)
                .originHub(originHub)
                .destHub(destHub)
                .currentHub(destHub)
                .customer(customer)
                .pickupShipper(pickupShipper)
                .deliveryShipper(deliveryShipper)
                .build();

        ShipmentTrackingLog log = ShipmentTrackingLog.builder()
                .shipment(shipment)
                .status(ShipmentStatus.IN_DEST_HUB)
                .hub(destHub)
                .title("Đã nhập kho Bưu cục Cầu Giấy")
                .description("Kiện hàng đã đến bưu cục nhận")
                .createdAt(fixedTime)
                .build();

        ShipmentStatusChangedEvent event = ShipmentStatusChangedEvent.from(shipment, log);

        assertThat(event.shipmentId()).isEqualTo(SHIPMENT_ID);
        assertThat(event.trackingCode()).isEqualTo(TRACKING_CODE);
        assertThat(event.status()).isEqualTo(ShipmentStatus.IN_DEST_HUB);
        assertThat(event.originHubId()).isEqualTo(ORIGIN_HUB_ID);
        assertThat(event.destHubId()).isEqualTo(DEST_HUB_ID);
        assertThat(event.currentHubId()).isEqualTo(DEST_HUB_ID);
        assertThat(event.currentHubName()).isEqualTo("Bưu cục Cầu Giấy (HN-CG01)");
        assertThat(event.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(event.pickupShipperId()).isEqualTo(PICKUP_SHIPPER_ID);
        assertThat(event.deliveryShipperId()).isEqualTo(DELIVERY_SHIPPER_ID);
        assertThat(event.logTitle()).isEqualTo("Đã nhập kho Bưu cục Cầu Giấy");
        assertThat(event.logDescription()).isEqualTo("Kiện hàng đã đến bưu cục nhận");
        assertThat(event.timestamp()).isEqualTo(fixedTime);
    }
}
