package com.uteexpress.service;

import com.uteexpress.dto.ws.ChatMessageDto;
import com.uteexpress.entity.ChatMessage;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.repository.ChatMessageRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private UserRepository userRepository;

    private ChatService chatService;

    private Hub hubHcm;
    private Hub hubHn;
    private Hub hubDaNang;
    private User customerOwner;
    private User otherCustomer;
    private User staffHcm;
    private User staffDaNang;
    private User shipperHcm;
    private User admin;
    private Shipment shipment;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatMessageRepository, shipmentRepository, userRepository);

        hubHcm = Hub.builder().id(1L).code("HCM-TD01").build();
        hubHn = Hub.builder().id(2L).code("HN-CG01").build();
        hubDaNang = Hub.builder().id(3L).code("DNG-HC01").build();

        customerOwner = User.builder().id(10L).fullName("Shop A").role(Role.CUSTOMER).build();
        otherCustomer = User.builder().id(11L).fullName("Shop B").role(Role.CUSTOMER).build();
        staffHcm = User.builder().id(20L).fullName("Staff HCM").role(Role.STAFF).hub(hubHcm).build();
        staffDaNang = User.builder().id(21L).fullName("Staff DN").role(Role.STAFF).hub(hubDaNang).build();
        shipperHcm = User.builder().id(30L).fullName("Shipper HCM").role(Role.SHIPPER).hub(hubHcm).build();
        admin = User.builder().id(1L).fullName("Admin").role(Role.ADMIN).build();

        shipment = Shipment.builder()
                .id(100L)
                .trackingCode("UTE261120CHAT01")
                .customer(customerOwner)
                .originHub(hubHcm)
                .destHub(hubHn)
                .currentHub(hubHcm)
                .build();
    }

    @Test
    void tcWs03_ownerHubStaffAndAdminCanSendAndReadMessages() {
        when(shipmentRepository.findById(100L)).thenReturn(Optional.of(shipment));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(55L);
            return m;
        });

        ChatMessageDto sentByCustomer = chatService.sendMessage(100L, customerOwner, "Nhờ giao sau 14h ạ");
        assertThat(sentByCustomer.id()).isEqualTo(55L);
        assertThat(sentByCustomer.senderName()).isEqualTo("Shop A");
        assertThat(sentByCustomer.senderRole()).isEqualTo("CUSTOMER");
        assertThat(sentByCustomer.content()).isEqualTo("Nhờ giao sau 14h ạ");

        assertThat(chatService.canAccessChat(shipment, staffHcm)).isTrue();
        assertThat(chatService.canAccessChat(shipment, admin)).isTrue();

        when(chatMessageRepository.findByShipmentIdOrderByCreatedAtAscIdAsc(100L))
                .thenReturn(List.of(ChatMessage.builder()
                        .id(55L).shipment(shipment).sender(customerOwner)
                        .content("Nhờ giao sau 14h ạ").createdAt(OffsetDateTime.now()).build()));
        assertThat(chatService.getMessages(100L, staffHcm)).hasSize(1);
    }

    @Test
    void tcWs04_otherCustomerUnrelatedStaffAndShipperAreDeniedAccess() {
        when(shipmentRepository.findById(100L)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> chatService.getMessages(100L, otherCustomer))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> chatService.sendMessage(100L, shipperHcm, "Shipper nhắn"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> chatService.getMessages(100L, staffDaNang))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void blankMessageRejectedWithBusinessException() {
        assertThatThrownBy(() -> chatService.sendMessage(100L, customerOwner, "   "))
                .isInstanceOf(BusinessException.class);
    }
}
