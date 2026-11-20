package com.uteexpress.dto.customer;

import com.uteexpress.enums.InspectionPolicy;
import com.uteexpress.enums.PayerType;
import com.uteexpress.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShipmentRequest {

    // ==================== SENDER ====================

    @NotBlank(message = "Vui lòng nhập tên người gửi")
    @Size(max = 120, message = "Tên người gửi tối đa 120 ký tự")
    private String senderName;

    @NotBlank(message = "Vui lòng nhập số điện thoại người gửi")
    @Size(max = 20, message = "Số điện thoại người gửi tối đa 20 ký tự")
    private String senderPhone;

    @NotBlank(message = "Vui lòng chọn tỉnh/thành người gửi")
    @Size(max = 100, message = "Tỉnh/thành người gửi tối đa 100 ký tự")
    private String senderProvince;

    @NotBlank(message = "Vui lòng nhập quận/huyện người gửi")
    @Size(max = 100, message = "Quận/huyện người gửi tối đa 100 ký tự")
    private String senderDistrict;

    @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết người gửi")
    @Size(max = 255, message = "Địa chỉ người gửi tối đa 255 ký tự")
    private String senderAddress;

    // ==================== RECEIVER ====================

    @NotBlank(message = "Vui lòng nhập tên người nhận")
    @Size(max = 120, message = "Tên người nhận tối đa 120 ký tự")
    private String receiverName;

    @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
    @Size(max = 20, message = "Số điện thoại người nhận tối đa 20 ký tự")
    private String receiverPhone;

    @NotBlank(message = "Vui lòng chọn tỉnh/thành người nhận")
    @Size(max = 100, message = "Tỉnh/thành người nhận tối đa 100 ký tự")
    private String receiverProvince;

    @NotBlank(message = "Vui lòng nhập quận/huyện người nhận")
    @Size(max = 100, message = "Quận/huyện người nhận tối đa 100 ký tự")
    private String receiverDistrict;

    @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết người nhận")
    @Size(max = 255, message = "Địa chỉ người nhận tối đa 255 ký tự")
    private String receiverAddress;

    // ==================== ITEM ====================

    @NotBlank(message = "Vui lòng nhập tên hàng hóa")
    @Size(max = 200, message = "Tên hàng hóa tối đa 200 ký tự")
    private String itemName;

    @NotNull(message = "Vui lòng nhập trọng lượng thực")
    @DecimalMin(value = "0.01", message = "Trọng lượng thực phải lớn hơn 0 kg")
    private BigDecimal actualWeightKg;

    @Min(value = 0, message = "Chiều dài không được âm")
    private Integer lengthCm;

    @Min(value = 0, message = "Chiều rộng không được âm")
    private Integer widthCm;

    @Min(value = 0, message = "Chiều cao không được âm")
    private Integer heightCm;

    private boolean isFragile;

    @Builder.Default
    @NotNull(message = "Vui lòng chọn quy định đồng kiểm")
    private InspectionPolicy inspectionPolicy = InspectionPolicy.ALLOW_VIEW_NO_TRY;

    private String note;

    // ==================== SERVICE & PAYMENT ====================

    @NotNull(message = "Vui lòng chọn gói dịch vụ vận chuyển")
    private Long serviceTypeId;

    @Builder.Default
    @DecimalMin(value = "0", message = "Tiền thu hộ COD không được âm")
    private BigDecimal codAmount = BigDecimal.ZERO;

    @Builder.Default
    @NotNull(message = "Vui lòng chọn người trả cước")
    private PayerType payerType = PayerType.SENDER;

    @Builder.Default
    @NotNull(message = "Vui lòng chọn hình thức thanh toán")
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    public boolean isFragile() {
        return isFragile;
    }

    public boolean getIsFragile() {
        return isFragile;
    }

    public void setIsFragile(boolean isFragile) {
        this.isFragile = isFragile;
    }

    public void setFragile(boolean isFragile) {
        this.isFragile = isFragile;
    }
}
