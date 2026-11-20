package com.uteexpress;

import com.uteexpress.config.WebSocketAuthChannelInterceptor;
import com.uteexpress.entity.CodSettlement;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Notification;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.*;
import com.uteexpress.repository.CodSettlementRepository;
import com.uteexpress.repository.HubRepository;
import com.uteexpress.repository.NotificationRepository;
import com.uteexpress.repository.ServiceTypeRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase4WsAuthTrackingAndCodTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebSocketAuthChannelInterceptor wsInterceptor;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private CodSettlementRepository codSettlementRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final MessageChannel NOOP_CHANNEL = new MessageChannel() {
        @Override
        public boolean send(Message<?> message) {
            return true;
        }

        @Override
        public boolean send(Message<?> message, long timeout) {
            return true;
        }
    };

    @Test
    void tcWs04WebSocketChannelInterceptorEnforcesStrictRoomAccessAndAntiIdor() {
        Shipment sample = shipmentRepository.findByTrackingCodeIgnoreCase("UTE261120A8K4P2").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String intruderEmail = "ws.intruder." + suffix + "@uteexpress.vn";
        User customerB = userRepository.save(User.builder()
                .email(intruderEmail)
                .phone("0955" + suffix)
                .passwordHash(passwordEncoder.encode("123456"))
                .fullName("Customer B WS")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());

        try {
            // Public tracking SUBSCRIBE allowed anonymously; SEND blocked
            assertThat(wsInterceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/tracking/" + sample.getTrackingCode(), null),
                    NOOP_CHANNEL)).isNotNull();
            assertThatThrownBy(() -> wsInterceptor.preSend(
                    stompMessage(StompCommand.SEND, "/topic/tracking/" + sample.getTrackingCode(), null),
                    NOOP_CHANNEL)).isInstanceOf(AccessDeniedException.class);

            // Personal notification queue blocks anonymous
            assertThatThrownBy(() -> wsInterceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/user/queue/notifications", null),
                    NOOP_CHANNEL)).isInstanceOf(AccessDeniedException.class);

            // Chat room: Customer A owner & Origin/Dest Staff ALLOWED
            assertThat(wsInterceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/chat/" + sample.getId(), "shop@uteexpress.vn"),
                    NOOP_CHANNEL)).isNotNull();
            assertThat(wsInterceptor.preSend(
                    stompMessage(StompCommand.SEND, "/app/chat/" + sample.getId(), "staff.hcm@uteexpress.vn"),
                    NOOP_CHANNEL)).isNotNull();
            assertThat(wsInterceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/chat/" + sample.getId(), "staff.hn@uteexpress.vn"),
                    NOOP_CHANNEL)).isNotNull();

            // Chat room: Anonymous, Customer B (IDOR), and Shipper BLOCKED
            assertThatThrownBy(() -> wsInterceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/chat/" + sample.getId(), null),
                    NOOP_CHANNEL)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> wsInterceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/chat/" + sample.getId(), intruderEmail),
                    NOOP_CHANNEL)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> wsInterceptor.preSend(
                    stompMessage(StompCommand.SEND, "/app/chat/" + sample.getId(), "shipper.hcm@uteexpress.vn"),
                    NOOP_CHANNEL)).isInstanceOf(AccessDeniedException.class);
        } finally {
            userRepository.deleteById(customerB.getId());
        }
    }

    @Test
    void tcWs02MultiCodeTrackingValidInvalidAndMax5Limit() throws Exception {
        // 2 valid + 1 invalid code
        mockMvc.perform(get("/tracking").param("codes", "UTE261120A8K4P2,UTE261119B7M2Q9,UTEINVALID999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UTE261120A8K4P2")))
                .andExpect(content().string(containsString("UTE261119B7M2Q9")))
                .andExpect(content().string(containsString("UTEINVALID999")));

        // 6 codes -> exceeds max 5 limit warning
        mockMvc.perform(get("/tracking").param("codes", "C1,C2,C3,C4,C5,C6"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("5")));
    }

    @Test
    void tcCod01AndTcCod02TwoShipmentsFeeDeductionBankSnapshotAndNoDoubleSettlement() throws Exception {
        String adminToken = loginAndExtractToken("admin@uteexpress.vn", "123456");
        String staffHcmToken = loginAndExtractToken("staff.hcm@uteexpress.vn", "123456");

        Hub hcmHub = hubRepository.findByCodeIgnoreCase("HCM-TD01").orElseThrow();
        ServiceType standard = serviceTypeRepository.findByCodeIgnoreCase("STANDARD").orElseThrow();
        User shipperHcm = userRepository.findByEmailIgnoreCase("shipper.hcm@uteexpress.vn").orElseThrow();

        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String email = "shop.cod2." + suffix + "@uteexpress.vn";
        User shop = userRepository.save(User.builder()
                .email(email)
                .phone("0944" + suffix)
                .passwordHash(passwordEncoder.encode("123456"))
                .fullName("Shop Đối Soát 2 Đơn")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .bankName("Techcombank")
                .bankAccountNumber("1903888999")
                .bankAccountHolder("SHOP DOI SOAT")
                .build());

        // Shipment 1: COD 300,000, VNPAY PAID (fee MUST NOT be deducted)
        Shipment s1 = shipmentRepository.save(buildDeliveredCodShipment(
                "UTECOD1" + suffix, shop, standard, hcmHub, shipperHcm,
                new BigDecimal("300000.00"), new BigDecimal("25000.00"),
                PayerType.SENDER, PaymentMethod.VNPAY, PaymentStatus.PAID));

        // Shipment 2: COD 200,000, SENDER CASH UNPAID fee 30,000 (fee MUST be deducted per BR-09)
        Shipment s2 = shipmentRepository.save(buildDeliveredCodShipment(
                "UTECOD2" + suffix, shop, standard, hcmHub, shipperHcm,
                new BigDecimal("200000.00"), new BigDecimal("30000.00"),
                PayerType.SENDER, PaymentMethod.CASH, PaymentStatus.UNPAID));

        Long settlementId = null;
        try {
            // Staff collects COD from Shipper for both shipments
            mockMvc.perform(post("/staff/cod/collect")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("shipmentIds", String.valueOf(s1.getId()), String.valueOf(s2.getId())))
                    .andExpect(status().is3xxRedirection());

            // Admin creates settlement -> totalCod=500,000, deduction=30,000, netAmount=470,000
            mockMvc.perform(post("/admin/cod-settlements")
                            .cookie(new Cookie("ACCESS_TOKEN", adminToken))
                            .param("customerId", String.valueOf(shop.getId()))
                            .param("note", "TC-COD-01 2 đơn"))
                    .andExpect(status().is3xxRedirection());

            List<CodSettlement> list = codSettlementRepository.findByCustomerIdOrderByCreatedAtDesc(shop.getId());
            assertThat(list).hasSize(1);
            CodSettlement created = list.get(0);
            settlementId = created.getId();
            assertThat(created.getTotalCodAmount()).isEqualByComparingTo("500000.00");
            assertThat(created.getTotalShippingDeduction()).isEqualByComparingTo("30000.00");
            assertThat(created.getNetAmount()).isEqualByComparingTo("470000.00");
            assertThat(created.getBankName()).isEqualTo("Techcombank");
            assertThat(created.getBankAccountNumber()).isEqualTo("1903888999");

            // TC-COD-02: Admin marks SETTLED -> both shipments become SETTLED, s2 becomes PAID, notif sent
            mockMvc.perform(post("/admin/cod-settlements/" + settlementId + "/settle")
                            .cookie(new Cookie("ACCESS_TOKEN", adminToken))
                            .param("transactionRef", "TCB-REF-" + suffix))
                    .andExpect(status().is3xxRedirection());

            assertThat(codSettlementRepository.findById(settlementId).orElseThrow().getStatus())
                    .isEqualTo(SettlementStatus.SETTLED);
            assertThat(shipmentRepository.findById(s1.getId()).orElseThrow().getCodStatus())
                    .isEqualTo(CodStatus.SETTLED);
            Shipment s2Settled = shipmentRepository.findById(s2.getId()).orElseThrow();
            assertThat(s2Settled.getCodStatus()).isEqualTo(CodStatus.SETTLED);
            assertThat(s2Settled.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(notificationRepository.countByUserIdAndIsReadFalse(shop.getId())).isGreaterThanOrEqualTo(1L);

            // Attempting to create another settlement for these 2 settled shipments MUST be rejected
            mockMvc.perform(post("/admin/cod-settlements")
                            .cookie(new Cookie("ACCESS_TOKEN", adminToken))
                            .param("customerId", String.valueOf(shop.getId())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("errorMessage"));
            assertThat(codSettlementRepository.findByCustomerIdOrderByCreatedAtDesc(shop.getId())).hasSize(1);
        } finally {
            List<Notification> notifs = notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(shop.getId());
            if (!notifs.isEmpty()) {
                notificationRepository.deleteAll(notifs);
            }
            for (Long sid : List.of(s1.getId(), s2.getId())) {
                shipmentRepository.findById(sid).ifPresent(sh -> {
                    sh.setSettlement(null);
                    shipmentRepository.save(sh);
                    shipmentRepository.deleteById(sh.getId());
                });
            }
            if (settlementId != null) {
                codSettlementRepository.deleteById(settlementId);
            }
            userRepository.deleteById(shop.getId());
        }
    }

    private Shipment buildDeliveredCodShipment(String code, User customer, ServiceType serviceType,
                                               Hub hub, User shipper, BigDecimal codAmount, BigDecimal totalFee,
                                               PayerType payerType, PaymentMethod method, PaymentStatus payStatus) {
        return Shipment.builder()
                .trackingCode(code)
                .customer(customer)
                .serviceType(serviceType)
                .senderName(customer.getFullName())
                .senderPhone(customer.getPhone())
                .senderProvince("TP. Hồ Chí Minh")
                .senderDistrict("TP. Thủ Đức")
                .senderAddress("01 Võ Văn Ngân")
                .receiverName("Người Nhận COD")
                .receiverPhone("0912345678")
                .receiverProvince("TP. Hồ Chí Minh")
                .receiverDistrict("TP. Thủ Đức")
                .receiverAddress("50 Đặng Văn Bi")
                .originHub(hub)
                .destHub(hub)
                .currentHub(hub)
                .pickupShipper(shipper)
                .deliveryShipper(shipper)
                .itemName("Hàng COD " + code)
                .actualWeightKg(new BigDecimal("1.00"))
                .chargeableWeightKg(new BigDecimal("1.00"))
                .isFragile(false)
                .inspectionPolicy(InspectionPolicy.ALLOW_VIEW_NO_TRY)
                .shippingFee(totalFee)
                .codFee(BigDecimal.ZERO)
                .totalFee(totalFee)
                .codAmount(codAmount)
                .payerType(payerType)
                .paymentMethod(method)
                .paymentStatus(payStatus)
                .status(ShipmentStatus.DELIVERED)
                .codStatus(CodStatus.HELD_BY_SHIPPER)
                .deliveredAt(OffsetDateTime.now().minusHours(1))
                .build();
    }

    private Message<?> stompMessage(StompCommand command, String destination, String principalEmail) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (principalEmail != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(principalEmail, null, List.of()));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        int start = setCookie.indexOf("ACCESS_TOKEN=") + "ACCESS_TOKEN=".length();
        int end = setCookie.indexOf(';', start);
        return end > 0 ? setCookie.substring(start, end) : setCookie.substring(start);
    }
}
