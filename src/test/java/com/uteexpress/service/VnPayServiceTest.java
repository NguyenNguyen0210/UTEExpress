package com.uteexpress.service;

import com.uteexpress.config.VnPayConfig;
import com.uteexpress.dto.payment.VnPayProcessResult;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.enums.HubType;
import com.uteexpress.enums.PayerType;
import com.uteexpress.enums.PaymentMethod;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VnPayServiceTest {

    private static final String TEST_TMN_CODE = "UTEEXP01";
    private static final String TEST_HASH_SECRET = "UTEEXPRESSVNPAYSANDBOXSECRETKEY2026";
    private static final String TEST_PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String TEST_RETURN_URL = "http://localhost:8080/payment/vnpay-return";

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentService shipmentService;

    private VnPayConfig vnPayConfig;
    private VnPayService vnPayService;
    private Hub originHub;
    private Shipment pendingShipment;

    @BeforeEach
    void setUp() {
        vnPayConfig = new VnPayConfig();
        vnPayConfig.setTmnCode(TEST_TMN_CODE);
        vnPayConfig.setHashSecret(TEST_HASH_SECRET);
        vnPayConfig.setPayUrl(TEST_PAY_URL);
        vnPayConfig.setReturnUrl(TEST_RETURN_URL);

        vnPayService = new VnPayService(vnPayConfig, shipmentRepository, shipmentService);

        originHub = Hub.builder()
                .id(1L)
                .code("HCM-TD01")
                .name("Bưu cục Thủ Đức")
                .type(HubType.POST_OFFICE)
                .province("TP. Hồ Chí Minh")
                .district("TP. Thủ Đức")
                .active(true)
                .build();

        pendingShipment = Shipment.builder()
                .id(42L)
                .trackingCode("UTE261120A8K4P2")
                .originHub(originHub)
                .totalFee(new BigDecimal("45000"))
                .payerType(PayerType.SENDER)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .status(ShipmentStatus.PENDING_PAYMENT)
                .vnpTxnRef("UTE261120A8K4P2_1732090000000")
                .build();

        lenient().when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("createPaymentUrl generates valid VNPay v2.1.0 signed URL that passes verifySignature")
    void createPaymentUrl_generatesValidSignedUrlAndPassesVerifySignature() throws Exception {
        String payUrl = vnPayService.createPaymentUrl(pendingShipment, "0:0:0:0:0:0:0:1");

        assertThat(payUrl).startsWith(TEST_PAY_URL + "?");
        assertThat(pendingShipment.getVnpTxnRef()).startsWith("UTE261120A8K4P2_");
        assertThat(pendingShipment.getVnpTxnRef().length()).isLessThanOrEqualTo(64);
        verify(shipmentRepository, times(1)).save(pendingShipment);

        Map<String, String> queryParams = parseDecodedQueryParams(payUrl);
        assertThat(queryParams.get("vnp_Version")).isEqualTo("2.1.0");
        assertThat(queryParams.get("vnp_Command")).isEqualTo("pay");
        assertThat(queryParams.get("vnp_TmnCode")).isEqualTo(TEST_TMN_CODE);
        assertThat(queryParams.get("vnp_Amount")).isEqualTo("4500000");
        assertThat(queryParams.get("vnp_CurrCode")).isEqualTo("VND");
        assertThat(queryParams.get("vnp_TxnRef")).isEqualTo(pendingShipment.getVnpTxnRef());
        assertThat(queryParams.get("vnp_OrderInfo")).isEqualTo("Thanh toan cuoc van don UTE261120A8K4P2");
        assertThat(queryParams.get("vnp_OrderType")).isEqualTo("other");
        assertThat(queryParams.get("vnp_Locale")).isEqualTo("vn");
        assertThat(queryParams.get("vnp_ReturnUrl")).isEqualTo(TEST_RETURN_URL);
        assertThat(queryParams.get("vnp_IpAddr")).isEqualTo("127.0.0.1");
        assertThat(queryParams.get("vnp_CreateDate")).matches("^\\d{14}$");
        assertThat(queryParams.get("vnp_ExpireDate")).matches("^\\d{14}$");
        assertThat(queryParams.get("vnp_SecureHash")).matches("^[0-9a-f]{128}$");

        assertThat(vnPayService.verifySignature(queryParams)).isTrue();
    }

    @Test
    @DisplayName("TC-VNPAY-04: Tampering vnp_Amount fails verifySignature and processCallback returns RspCode=97 without DB update")
    void tcVnpay04_tamperedAmount_failsSignatureAndReturns97() {
        Map<String, String> signedCallback = buildSignedCallbackParams(
                pendingShipment.getVnpTxnRef(),
                "4500000",
                "00",
                "00",
                "14588992"
        );
        // Tamper amount after signing
        signedCallback.put("vnp_Amount", "100000");

        assertThat(vnPayService.verifySignature(signedCallback)).isFalse();

        VnPayProcessResult result = vnPayService.processCallback(signedCallback);

        assertThat(result.validSignature()).isFalse();
        assertThat(result.paymentSuccess()).isFalse();
        assertThat(result.alreadyProcessed()).isFalse();
        assertThat(result.rspCode()).isEqualTo("97");
        assertThat(result.shipment()).isNull();
        assertThat(pendingShipment.getStatus()).isEqualTo(ShipmentStatus.PENDING_PAYMENT);
        assertThat(pendingShipment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);

        verify(shipmentRepository, never()).findByVnpTxnRef(any());
        verify(shipmentRepository, never()).save(any());
        verify(shipmentService, never()).recordTrackingLog(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-VNPAY-01: Valid callback vnp_ResponseCode=00 transitions PENDING_PAYMENT -> CREATED, marks PAID, and logs tracking")
    void tcVnpay01_validSuccessCallback_updatesShipmentAndRecordsTrackingLog() {
        Map<String, String> callback = buildSignedCallbackParams(
                pendingShipment.getVnpTxnRef(),
                "4500000",
                "00",
                "00",
                "14588992"
        );
        when(shipmentRepository.findByVnpTxnRef(pendingShipment.getVnpTxnRef()))
                .thenReturn(Optional.of(pendingShipment));

        VnPayProcessResult result = vnPayService.processCallback(callback);

        assertThat(result.validSignature()).isTrue();
        assertThat(result.paymentSuccess()).isTrue();
        assertThat(result.alreadyProcessed()).isFalse();
        assertThat(result.rspCode()).isEqualTo("00");
        assertThat(result.shipment()).isSameAs(pendingShipment);

        assertThat(pendingShipment.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(pendingShipment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pendingShipment.getVnpTransactionNo()).isEqualTo("14588992");
        assertThat(pendingShipment.getPaidAt()).isNotNull();

        verify(shipmentRepository, times(1)).save(pendingShipment);
        verify(shipmentService, times(1)).recordTrackingLog(
                eq(pendingShipment),
                eq(ShipmentStatus.CREATED),
                eq(originHub),
                isNull(),
                eq("Thanh toán VNPay thành công"),
                contains("#14588992")
        );
    }

    @Test
    @DisplayName("TC-VNPAY-03: Re-calling processCallback on already PAID shipment is idempotent (RspCode=02, no duplicate log)")
    void tcVnpay03_alreadyPaidShipment_isIdempotentAndDoesNotDuplicateLog() {
        pendingShipment.setPaymentStatus(PaymentStatus.PAID);
        pendingShipment.setStatus(ShipmentStatus.CREATED);
        pendingShipment.setVnpTransactionNo("14588992");

        Map<String, String> callback = buildSignedCallbackParams(
                pendingShipment.getVnpTxnRef(),
                "4500000",
                "00",
                "00",
                "14588992"
        );
        when(shipmentRepository.findByVnpTxnRef(pendingShipment.getVnpTxnRef()))
                .thenReturn(Optional.of(pendingShipment));

        VnPayProcessResult result = vnPayService.processCallback(callback);

        assertThat(result.validSignature()).isTrue();
        assertThat(result.paymentSuccess()).isTrue();
        assertThat(result.alreadyProcessed()).isTrue();
        assertThat(result.rspCode()).isEqualTo("02");
        assertThat(result.shipment()).isSameAs(pendingShipment);

        verify(shipmentRepository, never()).save(any());
        verify(shipmentService, never()).recordTrackingLog(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-VNPAY-02: Cancelled payment (vnp_ResponseCode=24) leaves shipment PENDING_PAYMENT and UNPAID")
    void tcVnpay02_cancelledPayment_keepsPendingPaymentAndUnpaid() {
        Map<String, String> callback = buildSignedCallbackParams(
                pendingShipment.getVnpTxnRef(),
                "4500000",
                "24",
                "02",
                "0"
        );
        when(shipmentRepository.findByVnpTxnRef(pendingShipment.getVnpTxnRef()))
                .thenReturn(Optional.of(pendingShipment));

        VnPayProcessResult result = vnPayService.processCallback(callback);

        assertThat(result.validSignature()).isTrue();
        assertThat(result.paymentSuccess()).isFalse();
        assertThat(result.alreadyProcessed()).isFalse();
        assertThat(result.rspCode()).isEqualTo("00");
        assertThat(result.message()).contains("24");
        assertThat(result.shipment()).isSameAs(pendingShipment);

        assertThat(pendingShipment.getStatus()).isEqualTo(ShipmentStatus.PENDING_PAYMENT);
        assertThat(pendingShipment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(pendingShipment.getPaidAt()).isNull();

        verify(shipmentRepository, never()).save(any());
        verify(shipmentService, never()).recordTrackingLog(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("processCallback returns RspCode=01 when shipment not found and RspCode=04 on amount mismatch")
    void processCallback_notFoundAndAmountMismatch_returnProperCodes() {
        Map<String, String> missingCallback = buildSignedCallbackParams("UNKNOWN_REF", "4500000", "00", "00", "123");
        when(shipmentRepository.findByVnpTxnRef("UNKNOWN_REF")).thenReturn(Optional.empty());

        VnPayProcessResult notFoundResult = vnPayService.processCallback(missingCallback);
        assertThat(notFoundResult.validSignature()).isTrue();
        assertThat(notFoundResult.paymentSuccess()).isFalse();
        assertThat(notFoundResult.rspCode()).isEqualTo("01");

        Map<String, String> wrongAmountCallback = buildSignedCallbackParams(
                pendingShipment.getVnpTxnRef(), "9999900", "00", "00", "123"
        );
        when(shipmentRepository.findByVnpTxnRef(pendingShipment.getVnpTxnRef()))
                .thenReturn(Optional.of(pendingShipment));

        VnPayProcessResult mismatchResult = vnPayService.processCallback(wrongAmountCallback);
        assertThat(mismatchResult.validSignature()).isTrue();
        assertThat(mismatchResult.paymentSuccess()).isFalse();
        assertThat(mismatchResult.rspCode()).isEqualTo("04");
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPaymentUrl rejects shipments that are already PAID or not in PENDING_PAYMENT status")
    void createPaymentUrl_rejectsInvalidShipmentState() {
        pendingShipment.setStatus(ShipmentStatus.CREATED);
        assertThatThrownBy(() -> vnPayService.createPaymentUrl(pendingShipment, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        pendingShipment.setStatus(ShipmentStatus.PENDING_PAYMENT);
        pendingShipment.setPaymentStatus(PaymentStatus.PAID);
        assertThatThrownBy(() -> vnPayService.createPaymentUrl(pendingShipment, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
    }

    private Map<String, String> buildSignedCallbackParams(String txnRef,
                                                          String amount,
                                                          String responseCode,
                                                          String txnStatus,
                                                          String txnNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Amount", amount);
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan cuoc van don UTE261120A8K4P2");
        params.put("vnp_PayDate", "20261120153000");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", txnStatus);
        params.put("vnp_TxnRef", txnRef);

        String signedQuery = vnPayService.buildSignedQuery(params, TEST_HASH_SECRET);
        return parseDecodedQueryParams("https://example.test/return?" + signedQuery);
    }

    private Map<String, String> parseDecodedQueryParams(String fullUrl) {
        String rawQuery = URI.create(fullUrl).getRawQuery();
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.US_ASCII);
            String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.US_ASCII);
            map.put(key, val);
        }
        return map;
    }
}
