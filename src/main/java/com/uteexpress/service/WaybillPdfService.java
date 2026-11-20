package com.uteexpress.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.uteexpress.config.AppProperties;
import com.uteexpress.entity.Shipment;
import com.uteexpress.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WaybillPdfService {

    private static final String FONT_REGULAR_PATH = "fonts/DejaVuSans.ttf";
    private static final String FONT_BOLD_PATH = "fonts/DejaVuSans-Bold.ttf";

    private static final Color COLOR_NAVY = new Color(13, 71, 161);
    private static final Color COLOR_ORANGE = new Color(234, 88, 12);
    private static final Color COLOR_ORANGE_LIGHT = new Color(251, 146, 60);
    private static final Color COLOR_GREEN = new Color(21, 128, 61);
    private static final Color COLOR_DARK = new Color(17, 24, 39);
    private static final Color COLOR_MUTED = new Color(75, 85, 99);
    private static final Color COLOR_BORDER = new Color(31, 41, 55);
    private static final Color COLOR_ROUTING_BG = new Color(239, 246, 255);
    private static final Color COLOR_FRAGILE_BG = new Color(254, 243, 199);
    private static final Color COLOR_FRAGILE_TEXT = new Color(185, 28, 28);
    private static final Color COLOR_COLLECT_BG = new Color(255, 247, 237);

    private final AppProperties appProperties;
    private final ShipmentService shipmentService;

    private volatile byte[] regularFontBytes;
    private volatile byte[] boldFontBytes;

    public byte[] generateWaybillPdf(Shipment shipment, String size) {
        if (shipment == null) {
            throw new BusinessException("Không tìm thấy thông tin vận đơn để xuất tem PDF");
        }

        boolean isA5 = "A5".equalsIgnoreCase(size != null ? size.trim() : "");
        Rectangle pageSize = isA5 ? PageSize.A5 : PageSize.A6;
        float margin = isA5 ? 16f : 10f;
        float scale = isA5 ? 1.35f : 1.0f;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BaseFont regularBase = createEmbeddedFont("DejaVuSans.ttf", getRegularFontBytes());
            BaseFont boldBase = createEmbeddedFont("DejaVuSans-Bold.ttf", getBoldFontBytes());

            Font brandWhiteFont = new Font(boldBase, 11.5f * scale, Font.NORMAL, Color.WHITE);
            Font brandOrangeFont = new Font(boldBase, 11.5f * scale, Font.NORMAL, COLOR_ORANGE_LIGHT);
            Font serviceHeaderFont = new Font(boldBase, 8.2f * scale, Font.NORMAL, Color.WHITE);
            Font trackingCodeFont = new Font(boldBase, 10.0f * scale, Font.NORMAL, COLOR_DARK);
            Font routingFont = new Font(boldBase, 14.5f * scale, Font.NORMAL, COLOR_NAVY);
            Font labelBoldFont = new Font(boldBase, 7.5f * scale, Font.NORMAL, COLOR_DARK);
            Font bodyRegularFont = new Font(regularBase, 7.5f * scale, Font.NORMAL, COLOR_DARK);
            Font bodyMutedFont = new Font(regularBase, 6.6f * scale, Font.NORMAL, COLOR_MUTED);
            Font fragileFont = new Font(boldBase, 7.2f * scale, Font.NORMAL, COLOR_FRAGILE_TEXT);
            Font collectLabelFont = new Font(boldBase, 7.0f * scale, Font.NORMAL, COLOR_DARK);
            Font collectAmountFont = new Font(boldBase, 12.0f * scale, Font.NORMAL, COLOR_ORANGE);
            Font paidAmountFont = new Font(boldBase, 8.8f * scale, Font.NORMAL, COLOR_GREEN);
            Font footerFont = new Font(regularBase, 6.8f * scale, Font.NORMAL, COLOR_MUTED);

            Document document = new Document(pageSize, margin, margin, margin, margin);
            PdfWriter.getInstance(document, baos);
            document.open();

            String trackingCode = safeText(shipment.getTrackingCode(), "N/A");
            document.addTitle("Tem vận đơn UTEExpress - " + trackingCode);
            document.addAuthor("UTEExpress Logistics");
            document.addSubject("Waybill " + (isA5 ? "A5" : "A6"));

            PdfPTable outer = new PdfPTable(1);
            outer.setWidthPercentage(100);

            // 1. Header: [UTEExpress] | GÓI: NHANH (EXPRESS)
            outer.addCell(buildHeaderCell(shipment, brandWhiteFont, brandOrangeFont, serviceHeaderFont, scale));

            // 2. Code128 Barcode + Tracking Code
            outer.addCell(buildBarcodeCell(trackingCode, trackingCodeFont, isA5, scale));

            // 3. Large Bold Routing Code: HCM-TD01 --> HN-CG01
            outer.addCell(buildRoutingCell(shipment, routingFont, scale));

            // 4. Sender block
            outer.addCell(buildPartyCell(
                    "TỪ: ",
                    shipment.getSenderName(),
                    shipment.getSenderPhone(),
                    formatFullAddress(shipment.getSenderAddress(), shipment.getSenderDistrict(), shipment.getSenderProvince()),
                    labelBoldFont,
                    bodyRegularFont,
                    scale
            ));

            // 5. Receiver block
            outer.addCell(buildPartyCell(
                    "ĐẾN: ",
                    shipment.getReceiverName(),
                    shipment.getReceiverPhone(),
                    formatFullAddress(shipment.getReceiverAddress(), shipment.getReceiverDistrict(), shipment.getReceiverProvince()),
                    labelBoldFont,
                    bodyRegularFont,
                    scale
            ));

            // 6. Lower 2-column section: Left (Item + Weight + Fragile + Inspection + Collect Amount) | Right (QR + Signature)
            outer.addCell(buildLowerSectionCell(
                    shipment,
                    trackingCode,
                    labelBoldFont,
                    bodyRegularFont,
                    bodyMutedFont,
                    fragileFont,
                    collectLabelFont,
                    collectAmountFont,
                    paidAmountFont,
                    isA5,
                    scale
            ));

            // 7. Footer
            PdfPCell footerCell = new PdfPCell(new Phrase(
                    "Lưu ý: Giao tối đa 3 lần trước khi chuyển hoàn.",
                    footerFont
            ));
            footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            footerCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            footerCell.setPaddingTop(3.5f * scale);
            footerCell.setPaddingBottom(3.5f * scale);
            footerCell.setPaddingLeft(5f * scale);
            footerCell.setPaddingRight(5f * scale);
            applyBorder(footerCell);
            outer.addCell(footerCell);

            document.add(outer);
            document.close();

            return baos.toByteArray();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể khởi tạo file PDF tem vận đơn: " + ex.getMessage(), ex);
        }
    }

    private PdfPCell buildHeaderCell(Shipment shipment,
                                     Font brandWhiteFont,
                                     Font brandOrangeFont,
                                     Font serviceHeaderFont,
                                     float scale) {
        PdfPTable headerTable = new PdfPTable(new float[]{42f, 58f});
        headerTable.setWidthPercentage(100);

        Phrase brandPhrase = new Phrase();
        brandPhrase.add(new Chunk("UTE", brandWhiteFont));
        brandPhrase.add(new Chunk("Express", brandOrangeFont));

        PdfPCell left = new PdfPCell(brandPhrase);
        left.setBorder(Rectangle.NO_BORDER);
        left.setBackgroundColor(COLOR_NAVY);
        left.setVerticalAlignment(Element.ALIGN_MIDDLE);
        left.setHorizontalAlignment(Element.ALIGN_LEFT);
        left.setPadding(5f * scale);
        headerTable.addCell(left);

        String serviceLabel = formatServiceHeader(shipment);
        PdfPCell right = new PdfPCell(new Phrase(serviceLabel, serviceHeaderFont));
        right.setBorder(Rectangle.NO_BORDER);
        right.setBackgroundColor(COLOR_NAVY);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPadding(5f * scale);
        headerTable.addCell(right);

        PdfPCell wrapper = new PdfPCell(headerTable);
        wrapper.setPadding(0f);
        applyBorder(wrapper);
        return wrapper;
    }

    private PdfPCell buildBarcodeCell(String trackingCode, Font trackingCodeFont, boolean isA5, float scale) throws Exception {
        byte[] barcodePng = generateBarcodePng(trackingCode, isA5 ? 480 : 360, isA5 ? 90 : 68);
        Image barcodeImg = Image.getInstance(barcodePng);
        barcodeImg.scaleToFit(isA5 ? 310f : 225f, isA5 ? 50f : 35f);
        barcodeImg.setAlignment(Element.ALIGN_CENTER);

        PdfPCell cell = new PdfPCell();
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(5f * scale);
        cell.setPaddingBottom(4f * scale);
        cell.setPaddingLeft(6f * scale);
        cell.setPaddingRight(6f * scale);
        applyBorder(cell);

        cell.addElement(barcodeImg);

        Paragraph codePara = new Paragraph(trackingCode, trackingCodeFont);
        codePara.setAlignment(Element.ALIGN_CENTER);
        codePara.setSpacingBefore(2f * scale);
        cell.addElement(codePara);

        return cell;
    }

    private PdfPCell buildRoutingCell(Shipment shipment, Font routingFont, float scale) {
        String originCode = shipment.getOriginHub() != null ? safeText(shipment.getOriginHub().getCode(), "---") : "---";
        String destCode = shipment.getDestHub() != null ? safeText(shipment.getDestHub().getCode(), "---") : "---";
        String routingText = originCode + "  -->  " + destCode;

        PdfPCell cell = new PdfPCell(new Phrase(routingText, routingFont));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(COLOR_ROUTING_BG);
        cell.setPaddingTop(5f * scale);
        cell.setPaddingBottom(5.5f * scale);
        cell.setPaddingLeft(4f * scale);
        cell.setPaddingRight(4f * scale);
        applyBorder(cell);
        return cell;
    }

    private PdfPCell buildPartyCell(String prefix,
                                    String name,
                                    String phone,
                                    String fullAddress,
                                    Font labelBoldFont,
                                    Font bodyRegularFont,
                                    float scale) {
        PdfPCell cell = new PdfPCell();
        cell.setPaddingTop(4f * scale);
        cell.setPaddingBottom(4.5f * scale);
        cell.setPaddingLeft(6f * scale);
        cell.setPaddingRight(6f * scale);
        applyBorder(cell);

        Paragraph line1 = new Paragraph();
        line1.setLeading(10f * scale);
        line1.add(new Chunk(prefix, labelBoldFont));
        line1.add(new Chunk(safeText(name, "—"), labelBoldFont));
        if (phone != null && !phone.isBlank()) {
            line1.add(new Chunk(" (" + phone.trim() + ")", bodyRegularFont));
        }
        cell.addElement(line1);

        Paragraph line2 = new Paragraph();
        line2.setLeading(10f * scale);
        line2.setSpacingBefore(1.5f * scale);
        line2.add(new Chunk("ĐC: ", labelBoldFont));
        line2.add(new Chunk(fullAddress, bodyRegularFont));
        cell.addElement(line2);

        return cell;
    }

    private PdfPCell buildLowerSectionCell(Shipment shipment,
                                           String trackingCode,
                                           Font labelBoldFont,
                                           Font bodyRegularFont,
                                           Font bodyMutedFont,
                                           Font fragileFont,
                                           Font collectLabelFont,
                                           Font collectAmountFont,
                                           Font paidAmountFont,
                                           boolean isA5,
                                           float scale) throws Exception {
        PdfPTable lowerTable = new PdfPTable(new float[]{62f, 38f});
        lowerTable.setWidthPercentage(100);

        // LEFT COLUMN: nested table (Item info + Receiver Collect Amount)
        PdfPTable leftTable = new PdfPTable(1);
        leftTable.setWidthPercentage(100);

        PdfPCell itemCell = new PdfPCell();
        itemCell.setBorder(Rectangle.NO_BORDER);
        itemCell.setPaddingTop(4.5f * scale);
        itemCell.setPaddingBottom(4.5f * scale);
        itemCell.setPaddingLeft(6f * scale);
        itemCell.setPaddingRight(5f * scale);

        Paragraph itemPara = new Paragraph();
        itemPara.setLeading(9.8f * scale);
        itemPara.add(new Chunk("Hàng hóa: ", labelBoldFont));
        itemPara.add(new Chunk(truncate(safeText(shipment.getItemName(), "—"), isA5 ? 85 : 55), bodyRegularFont));
        itemCell.addElement(itemPara);

        Paragraph weightPara = new Paragraph();
        weightPara.setLeading(10.5f * scale);
        weightPara.setSpacingBefore(2f * scale);
        weightPara.add(new Chunk("KL: ", labelBoldFont));
        weightPara.add(new Chunk(formatWeight(shipment.getChargeableWeightKg()) + " kg", bodyRegularFont));
        if (Boolean.TRUE.equals(shipment.getIsFragile())) {
            weightPara.add(new Chunk("   ", bodyRegularFont));
            Chunk fragileBadge = new Chunk(" [HÀNG DỄ VỠ] ", fragileFont);
            fragileBadge.setBackground(COLOR_FRAGILE_BG, 1.5f, 1.0f, 1.5f, 1.5f);
            weightPara.add(fragileBadge);
        }
        itemCell.addElement(weightPara);

        String inspectionLabel = shipment.getInspectionPolicy() != null
                ? shipment.getInspectionPolicy().getLabel()
                : "Cho xem hàng, không cho thử";
        Paragraph inspectPara = new Paragraph();
        inspectPara.setLeading(9.8f * scale);
        inspectPara.setSpacingBefore(2f * scale);
        inspectPara.add(new Chunk("Đồng kiểm: ", labelBoldFont));
        inspectPara.add(new Chunk(inspectionLabel, bodyRegularFont));
        itemCell.addElement(inspectPara);

        if (shipment.getNote() != null && !shipment.getNote().isBlank()) {
            Paragraph notePara = new Paragraph();
            notePara.setLeading(9.5f * scale);
            notePara.setSpacingBefore(2f * scale);
            notePara.add(new Chunk("Ghi chú: ", labelBoldFont));
            notePara.add(new Chunk(truncate(shipment.getNote().trim(), isA5 ? 95 : 60), bodyMutedFont));
            itemCell.addElement(notePara);
        }
        leftTable.addCell(itemCell);

        // Receiver collect amount box
        BigDecimal collectAmount = shipmentService.calculateReceiverCollectAmount(shipment);
        PdfPCell collectCell = new PdfPCell();
        collectCell.setBorder(Rectangle.TOP);
        collectCell.setBorderColor(COLOR_BORDER);
        collectCell.setBorderWidth(0.8f);
        collectCell.setBackgroundColor(COLOR_COLLECT_BG);
        collectCell.setPaddingTop(5f * scale);
        collectCell.setPaddingBottom(6f * scale);
        collectCell.setPaddingLeft(6f * scale);
        collectCell.setPaddingRight(5f * scale);

        Paragraph collectTitle = new Paragraph("TIỀN THU NGƯỜI NHẬN:", collectLabelFont);
        collectTitle.setLeading(9.5f * scale);
        collectCell.addElement(collectTitle);

        Paragraph collectValue;
        if (collectAmount != null && collectAmount.compareTo(BigDecimal.ZERO) > 0) {
            collectValue = new Paragraph(formatVnd(collectAmount) + " đ", collectAmountFont);
            collectValue.setAlignment(Element.ALIGN_CENTER);
            collectValue.setSpacingBefore(3f * scale);
        } else {
            collectValue = new Paragraph("0 đ — ĐÃ THANH TOÁN", paidAmountFont);
            collectValue.setAlignment(Element.ALIGN_CENTER);
            collectValue.setSpacingBefore(4f * scale);
        }
        collectCell.addElement(collectValue);
        leftTable.addCell(collectCell);

        PdfPCell leftWrapper = new PdfPCell(leftTable);
        leftWrapper.setPadding(0f);
        leftWrapper.setBorder(Rectangle.RIGHT);
        leftWrapper.setBorderColor(COLOR_BORDER);
        leftWrapper.setBorderWidth(0.8f);
        lowerTable.addCell(leftWrapper);

        // RIGHT COLUMN: nested table (QR Code + Signature Box)
        PdfPTable rightTable = new PdfPTable(1);
        rightTable.setWidthPercentage(100);

        String trackingUrl = buildTrackingUrl(trackingCode);
        byte[] qrPng = generateQrCodePng(trackingUrl, isA5 ? 240 : 180);
        Image qrImg = Image.getInstance(qrPng);
        float qrDisplaySize = isA5 ? 92f : 66f;
        qrImg.scaleToFit(qrDisplaySize, qrDisplaySize);
        qrImg.setAlignment(Element.ALIGN_CENTER);

        PdfPCell qrCell = new PdfPCell();
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrCell.setPaddingTop(4f * scale);
        qrCell.setPaddingBottom(3f * scale);
        qrCell.setPaddingLeft(4f * scale);
        qrCell.setPaddingRight(4f * scale);
        qrCell.addElement(qrImg);

        Paragraph qrCaption = new Paragraph("Quét QR tra cứu", bodyMutedFont);
        qrCaption.setAlignment(Element.ALIGN_CENTER);
        qrCaption.setSpacingBefore(1.5f * scale);
        qrCell.addElement(qrCaption);
        rightTable.addCell(qrCell);

        PdfPCell signCell = new PdfPCell();
        signCell.setBorder(Rectangle.TOP);
        signCell.setBorderColor(COLOR_BORDER);
        signCell.setBorderWidth(0.8f);
        signCell.setMinimumHeight(isA5 ? 74f : 50f);
        signCell.setPaddingTop(3.5f * scale);
        signCell.setPaddingBottom(4f * scale);
        signCell.setPaddingLeft(4f * scale);
        signCell.setPaddingRight(4f * scale);

        Paragraph signTitle = new Paragraph("Chữ ký người nhận", labelBoldFont);
        signTitle.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(signTitle);

        Paragraph signSub = new Paragraph("(Ký & ghi rõ họ tên)", bodyMutedFont);
        signSub.setAlignment(Element.ALIGN_CENTER);
        signSub.setSpacingBefore(1f * scale);
        signCell.addElement(signSub);
        rightTable.addCell(signCell);

        PdfPCell rightWrapper = new PdfPCell(rightTable);
        rightWrapper.setPadding(0f);
        rightWrapper.setBorder(Rectangle.NO_BORDER);
        lowerTable.addCell(rightWrapper);

        PdfPCell outerCell = new PdfPCell(lowerTable);
        outerCell.setPadding(0f);
        applyBorder(outerCell);
        return outerCell;
    }

    private byte[] generateBarcodePng(String content, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.CODE_128,
                    width,
                    height,
                    hints
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo mã vạch Code128 cho vận đơn", ex);
        }
    }

    private byte[] generateQrCodePng(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    hints
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo mã QR tra cứu cho vận đơn", ex);
        }
    }

    private BaseFont createEmbeddedFont(String fontName, byte[] fontBytes) throws IOException {
        return BaseFont.createFont(
                fontName,
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED,
                true,
                fontBytes,
                null
        );
    }

    private byte[] getRegularFontBytes() throws IOException {
        if (regularFontBytes == null) {
            synchronized (this) {
                if (regularFontBytes == null) {
                    regularFontBytes = loadClasspathBytes(FONT_REGULAR_PATH);
                }
            }
        }
        return regularFontBytes;
    }

    private byte[] getBoldFontBytes() throws IOException {
        if (boldFontBytes == null) {
            synchronized (this) {
                if (boldFontBytes == null) {
                    boldFontBytes = loadClasspathBytes(FONT_BOLD_PATH);
                }
            }
        }
        return boldFontBytes;
    }

    private byte[] loadClasspathBytes(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream is = resource.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private String buildTrackingUrl(String trackingCode) {
        String base = appProperties != null && appProperties.getBaseUrl() != null && !appProperties.getBaseUrl().isBlank()
                ? appProperties.getBaseUrl().trim()
                : "http://localhost:8080";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/tracking?codes=" + URLEncoder.encode(trackingCode, StandardCharsets.UTF_8);
    }

    private String formatServiceHeader(Shipment shipment) {
        if (shipment.getServiceType() == null) {
            return "GÓI: TIÊU CHUẨN";
        }
        String name = safeText(shipment.getServiceType().getName(), "TIÊU CHUẨN").toUpperCase(Locale.forLanguageTag("vi-VN"));
        String code = shipment.getServiceType().getCode();
        if (code != null && !code.isBlank()) {
            return "GÓI: " + name + " (" + code.trim().toUpperCase(Locale.ROOT) + ")";
        }
        return "GÓI: " + name;
    }

    private String formatFullAddress(String address, String district, String province) {
        StringBuilder sb = new StringBuilder();
        appendAddressPart(sb, address);
        appendAddressPart(sb, district);
        appendAddressPart(sb, province);
        return sb.isEmpty() ? "—" : sb.toString();
    }

    private void appendAddressPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(part.trim());
    }

    private String formatWeight(BigDecimal weight) {
        if (weight == null) {
            return "0";
        }
        BigDecimal stripped = weight.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return weight.setScale(1, RoundingMode.HALF_UP).toPlainString();
        }
        return stripped.toPlainString();
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(amount.setScale(0, RoundingMode.HALF_UP));
    }

    private String safeText(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private void applyBorder(PdfPCell cell) {
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidth(0.8f);
    }
}
