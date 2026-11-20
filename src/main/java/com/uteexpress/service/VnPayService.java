package com.uteexpress.service;

import com.uteexpress.config.VnPayConfig;
import com.uteexpress.dto.payment.VnPayProcessResult;
import com.uteexpress.entity.Shipment;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class VnPayService {

    private static final String VNP_VERSION = "2.1.0";
    private static final String VNP_COMMAND = "pay";
    private static final String VNP_CURR_CODE = "VND";
    private static final String VNP_ORDER_TYPE = "other";
    private static final String VNP_LOCALE = "vn";
    private static final int EXPIRE_MINUTES = 15;
    private static final int MAX_TXN_REF_LENGTH = 64;
    private static final BigDecimal AMOUNT_MULTIPLIER = BigDecimal.valueOf(100);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnPayConfig vnPayConfig;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;

    public String hmacSha512(String secretKey, String data) {
        if (secretKey == null || data == null) {
            throw new IllegalArgumentException("Secret key và dữ liệu ký HMAC-SHA512 không được null");
        }
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKeySpec);
            byte[] hashBytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo chữ ký HMAC-SHA512 cho VNPay", ex);
        }
    }

    public String buildSignedQuery(Map<String, String> params, String hashSecret) {
        TreeMap<String, String> sorted = filterAndSortParams(params);
        String hashData = buildHashData(sorted);
        String secureHash = hmacSha512(hashSecret, hashData);

        StringBuilder query = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                query.append('&');
            }
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
            first = false;
        }
        if (!query.isEmpty()) {
            query.append('&');
        }
        query.append("vnp_SecureHash=").append(secureHash);
        return query.toString();
    }

    public boolean verifySignature(Map<String, String> allParams) {
        if (allParams == null || allParams.isEmpty()) {
            return false;
        }
        String receivedHash = findParamIgnoreCase(allParams, "vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        String secret = vnPayConfig.getHashSecret();
        if (secret == null || secret.isBlank()) {
            return false;
        }

        TreeMap<String, String> sorted = filterAndSortParams(allParams);
        if (sorted.isEmpty()) {
            return false;
        }
        String hashData = buildHashData(sorted);
        String expectedHash = hmacSha512(secret, hashData);

        byte[] expectedBytes = expectedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] receivedBytes = receivedHash.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    @Transactional
    public String createPaymentUrl(Shipment shipment, String clientIp) {
        if (shipment == null) {
            throw new BusinessException("Không tìm thấy vận đơn để thanh toán VNPay");
        }
        if (shipment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Vận đơn này đã được thanh toán cước trực tuyến");
        }
        if (shipment.getStatus() != ShipmentStatus.PENDING_PAYMENT) {
            throw new BusinessException("Chỉ có thể thanh toán VNPay cho vận đơn đang ở trạng thái Chờ thanh toán");
        }
        if (shipment.getTotalFee() == null || shipment.getTotalFee().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Tổng cước phí vận đơn không hợp lệ để thanh toán VNPay");
        }

        String rawTxnRef = shipment.getTrackingCode() + "_" + System.currentTimeMillis();
        String txnRef = rawTxnRef.length() > MAX_TXN_REF_LENGTH
                ? rawTxnRef.substring(0, MAX_TXN_REF_LENGTH)
                : rawTxnRef;
        shipment.setVnpTxnRef(txnRef);
        shipmentRepository.save(shipment);

        long vnpAmount = shipment.getTotalFee()
                .multiply(AMOUNT_MULTIPLIER)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        ZonedDateTime nowVn = ZonedDateTime.now(VIETNAM_ZONE);
        ZonedDateTime expireVn = nowVn.plusMinutes(EXPIRE_MINUTES);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", VNP_VERSION);
        params.put("vnp_Command", VNP_COMMAND);
        params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        params.put("vnp_Amount", String.valueOf(vnpAmount));
        params.put("vnp_CurrCode", VNP_CURR_CODE);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan cuoc van don " + shipment.getTrackingCode());
        params.put("vnp_OrderType", VNP_ORDER_TYPE);
        params.put("vnp_Locale", VNP_LOCALE);
        params.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        params.put("vnp_IpAddr", normalizeClientIp(clientIp));
        params.put("vnp_CreateDate", nowVn.format(VNPAY_DATE_FORMAT));
        params.put("vnp_ExpireDate", expireVn.format(VNPAY_DATE_FORMAT));

        String signedQuery = buildSignedQuery(params, vnPayConfig.getHashSecret());
        return vnPayConfig.getPayUrl() + "?" + signedQuery;
    }

    @Transactional
    public VnPayProcessResult processCallback(Map<String, String> params) {
        // Step 1: Verify HMAC-SHA512 signature (TC-VNPAY-04)
        if (!verifySignature(params)) {
            return new VnPayProcessResult(false, false, false, "97", "Chữ ký VNPay không hợp lệ", null);
        }

        // Step 2: Locate shipment by vnp_TxnRef
        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return new VnPayProcessResult(true, false, false, "01", "Không tìm thấy vận đơn tương ứng giao dịch", null);
        }
        Optional<Shipment> shipmentOpt = shipmentRepository.findByVnpTxnRef(txnRef.trim());
        if (shipmentOpt.isEmpty()) {
            return new VnPayProcessResult(true, false, false, "01", "Không tìm thấy vận đơn tương ứng giao dịch", null);
        }
        Shipment shipment = shipmentOpt.get();

        // Step 3: Validate vnp_Amount matches totalFee * 100
        BigDecimal expectedAmount = shipment.getTotalFee() != null
                ? shipment.getTotalFee().multiply(AMOUNT_MULTIPLIER).setScale(0, RoundingMode.HALF_UP)
                : null;
        BigDecimal receivedAmount = parseAmount(params.get("vnp_Amount"));
        if (expectedAmount == null || receivedAmount == null || expectedAmount.compareTo(receivedAmount) != 0) {
            return new VnPayProcessResult(true, false, false, "04", "Số tiền thanh toán không khớp", shipment);
        }

        // Step 4: Idempotent check (TC-VNPAY-03)
        if (shipment.getPaymentStatus() == PaymentStatus.PAID) {
            return new VnPayProcessResult(true, true, true, "02", "Giao dịch đã được xác nhận trước đó", shipment);
        }

        // Step 5: Evaluate vnp_ResponseCode and vnp_TransactionStatus
        String respCode = params.get("vnp_ResponseCode");
        String txnStatus = params.get("vnp_TransactionStatus");
        boolean paymentSucceeded = "00".equals(respCode)
                && (txnStatus == null || txnStatus.isBlank() || "00".equals(txnStatus));

        if (paymentSucceeded) {
            String rawTxnNo = params.get("vnp_TransactionNo");
            String txnNo = (rawTxnNo != null && !rawTxnNo.isBlank()) ? rawTxnNo.trim() : txnRef.trim();

            shipment.setPaymentStatus(PaymentStatus.PAID);
            shipment.setVnpTransactionNo(txnNo);
            shipment.setPaidAt(OffsetDateTime.now());
            if (shipment.getStatus() == ShipmentStatus.PENDING_PAYMENT) {
                shipment.setStatus(ShipmentStatus.CREATED);
            }
            Shipment saved = shipmentRepository.save(shipment);

            shipmentService.recordTrackingLog(
                    saved,
                    ShipmentStatus.CREATED,
                    saved.getOriginHub(),
                    null,
                    "Thanh toán VNPay thành công",
                    "Giao dịch VNPay #" + txnNo + " đã xác nhận. Vận đơn sẵn sàng điều phối lấy hàng."
            );
            return new VnPayProcessResult(true, true, false, "00", "Thanh toán cước trực tuyến thành công", saved);
        }

        String displayCode = (respCode != null && !respCode.isBlank()) ? respCode.trim() : "UNKNOWN";
        return new VnPayProcessResult(
                true,
                false,
                false,
                "00",
                "Giao dịch thanh toán chưa hoàn tất hoặc đã bị hủy (Mã lỗi: " + displayCode + ")",
                shipment
        );
    }

    private TreeMap<String, String> filterAndSortParams(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (params == null) {
            return sorted;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank()) {
                continue;
            }
            if ("vnp_SecureHash".equalsIgnoreCase(key) || "vnp_SecureHashType".equalsIgnoreCase(key)) {
                continue;
            }
            if (value == null || value.isBlank()) {
                continue;
            }
            sorted.put(key, value);
        }
        return sorted;
    }

    private String buildHashData(TreeMap<String, String> sortedParams) {
        StringBuilder hashData = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (!first) {
                hashData.append('&');
            }
            hashData.append(entry.getKey())
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
            first = false;
        }
        return hashData.toString();
    }

    private String findParamIgnoreCase(Map<String, String> params, String targetKey) {
        if (params.containsKey(targetKey)) {
            return params.get(targetKey);
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(targetKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private BigDecimal parseAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(rawAmount.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "127.0.0.1";
        }
        String ip = clientIp.trim();
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
