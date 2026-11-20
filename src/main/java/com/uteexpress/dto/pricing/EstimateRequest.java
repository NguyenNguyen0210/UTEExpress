package com.uteexpress.dto.pricing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstimateRequest {

    @NotBlank(message = "Vui lòng chọn tỉnh/thành gửi")
    private String senderProvince;

    @NotBlank(message = "Vui lòng chọn tỉnh/thành nhận")
    private String receiverProvince;

    @NotNull(message = "Vui lòng nhập trọng lượng thực")
    @DecimalMin(value = "0.01", message = "Trọng lượng phải lớn hơn 0")
    private BigDecimal actualWeightKg;

    @Min(value = 0, message = "Chiều dài không được âm")
    private Integer lengthCm;

    @Min(value = 0, message = "Chiều rộng không được âm")
    private Integer widthCm;

    @Min(value = 0, message = "Chiều cao không được âm")
    private Integer heightCm;

    @Builder.Default
    @DecimalMin(value = "0", message = "Tiền thu hộ COD không được âm")
    private BigDecimal codAmount = BigDecimal.ZERO;

    private Long serviceTypeId;
}
