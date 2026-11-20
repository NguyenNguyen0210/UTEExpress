package com.uteexpress.service;

import com.uteexpress.dto.cod.SettlementDetailView;
import com.uteexpress.entity.CodSettlement;
import com.uteexpress.entity.Shipment;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class CsvExportService {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] exportShipmentsCsv(List<Shipment> shipments) {
        String[] headers = {
                "Mã vận đơn",
                "Ngày tạo",
                "Trạng thái",
                "Gói dịch vụ",
                "Bưu cục gửi",
                "Bưu cục nhận",
                "Vị trí hiện tại",
                "Người gửi",
                "SĐT gửi",
                "Người nhận",
                "SĐT nhận",
                "Địa chỉ nhận",
                "Tên hàng hóa",
                "KL tính cước (kg)",
                "Tiền COD (VNĐ)",
                "Tổng cước (VNĐ)",
                "Người trả cước",
                "TT Thanh toán",
                "TT COD"
        };

        return buildCsv(headers, writer -> {
            if (shipments == null) {
                return;
            }
            for (Shipment s : shipments) {
                if (s == null) {
                    continue;
                }
                String receiverAddr = joinAddress(s.getReceiverAddress(), s.getReceiverDistrict(), s.getReceiverProvince());
                writeRow(writer, new String[]{
                        safe(s.getTrackingCode()),
                        formatDateTime(s.getCreatedAt()),
                        s.getStatus() != null ? s.getStatus().getLabel() : "",
                        s.getServiceType() != null ? s.getServiceType().getName() : "",
                        s.getOriginHub() != null ? s.getOriginHub().getCode() : "",
                        s.getDestHub() != null ? s.getDestHub().getCode() : "",
                        s.getCurrentHub() != null ? s.getCurrentHub().getCode() : "",
                        safe(s.getSenderName()),
                        safe(s.getSenderPhone()),
                        safe(s.getReceiverName()),
                        safe(s.getReceiverPhone()),
                        receiverAddr,
                        safe(s.getItemName()),
                        formatDecimal(s.getChargeableWeightKg()),
                        formatAmount(s.getCodAmount()),
                        formatAmount(s.getTotalFee()),
                        s.getPayerType() != null ? s.getPayerType().getLabel() : "",
                        s.getPaymentStatus() != null ? s.getPaymentStatus().getLabel() : "",
                        s.getCodStatus() != null ? s.getCodStatus().getLabel() : ""
                });
            }
        });
    }

    public byte[] exportCodSettlementsCsv(List<SettlementDetailView> views) {
        String[] headers = {
                "Mã phiếu đối soát",
                "Ngày lập",
                "Khách hàng",
                "Email",
                "Ngân hàng",
                "Số tài khoản",
                "Chủ tài khoản",
                "Số vận đơn",
                "Tổng tiền COD (VNĐ)",
                "Cấn trừ cước (VNĐ)",
                "Số tiền thực chuyển (VNĐ)",
                "Trạng thái",
                "Mã GD chuyển khoản",
                "Thời gian thanh toán",
                "Ghi chú"
        };

        return buildCsv(headers, writer -> {
            if (views == null) {
                return;
            }
            for (SettlementDetailView view : views) {
                if (view == null || view.settlement() == null) {
                    continue;
                }
                CodSettlement st = view.settlement();
                int shipmentCount = view.shipments() != null ? view.shipments().size() : 0;
                writeRow(writer, new String[]{
                        safe(st.getSettlementCode()),
                        formatDateTime(st.getCreatedAt()),
                        st.getCustomer() != null ? safe(st.getCustomer().getFullName()) : "",
                        st.getCustomer() != null ? safe(st.getCustomer().getEmail()) : "",
                        safe(st.getBankName()),
                        safe(st.getBankAccountNumber()),
                        safe(st.getBankAccountHolder()),
                        String.valueOf(shipmentCount),
                        formatAmount(st.getTotalCodAmount()),
                        formatAmount(st.getTotalShippingDeduction()),
                        formatAmount(st.getNetAmount()),
                        st.getStatus() != null ? st.getStatus().getLabel() : "",
                        safe(st.getTransactionRef()),
                        formatDateTime(st.getSettledAt()),
                        safe(st.getNote())
                });
            }
        });
    }

    @FunctionalInterface
    public interface CsvRowWriter {
        void writeRows(Writer writer) throws IOException;
    }

    public byte[] buildCsv(String[] headers, CsvRowWriter rowWriter) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            baos.write(UTF8_BOM);
            try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
                writeRow(writer, headers);
                rowWriter.writeRows(writer);
                writer.flush();
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể xuất file CSV: " + ex.getMessage(), ex);
        }
    }

    public void writeRow(Writer writer, String[] cols) throws IOException {
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCsvCell(cols[i]));
        }
        writer.write("\r\n");
    }

    public String escapeCsvCell(String raw) {
        if (raw == null) {
            return "\"\"";
        }
        String value = raw.replace('\r', ' ').replace('\n', ' ').trim();
        if (!value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
                value = "'" + value;
            }
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    public String formatDateTime(OffsetDateTime dt) {
        if (dt == null) {
            return "";
        }
        return dt.atZoneSameInstant(VIETNAM_ZONE).format(DATE_TIME_FMT);
    }

    public String formatAmount(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().scale() <= 0
                ? value.toBigInteger().toString()
                : value.toPlainString();
    }

    public String formatDecimal(BigDecimal value) {
        return value != null ? value.toPlainString() : "0";
    }

    private String safe(String s) {
        return s != null ? s : "";
    }

    private String joinAddress(String addr, String district, String province) {
        StringBuilder sb = new StringBuilder();
        if (addr != null && !addr.isBlank()) sb.append(addr.trim());
        if (district != null && !district.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(district.trim());
        }
        if (province != null && !province.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(province.trim());
        }
        return sb.toString();
    }
}
