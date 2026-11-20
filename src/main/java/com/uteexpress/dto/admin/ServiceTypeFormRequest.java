package com.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceTypeFormRequest {

    private Long id;

    @NotBlank(message = "Vui lòng nhập mã gói dịch vụ")
    @Size(max = 30)
    private String code;

    @NotBlank(message = "Vui lòng nhập tên gói dịch vụ")
    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String description;

    @NotNull(message = "Vui lòng nhập cước cơ bản")
    @DecimalMin(value = "0", message = "Cước cơ bản không được âm")
    private BigDecimal baseFee;

    @NotNull(message = "Vui lòng nhập trọng lượng chuẩn")
    @DecimalMin(value = "0.5", message = "Trọng lượng chuẩn tối thiểu 0.5 kg")
    private BigDecimal baseWeightKg = new BigDecimal("1.00");

    @NotNull(message = "Vui lòng nhập đơn giá vượt ngưỡng")
    @DecimalMin(value = "0", message = "Đơn giá vượt ngưỡng không được âm")
    private BigDecimal extraFeePerHalfKg;

    @NotNull(message = "Vui lòng nhập phụ phí liên tỉnh")
    @DecimalMin(value = "0", message = "Phụ phí liên tỉnh không được âm")
    private BigDecimal interProvinceSurcharge = BigDecimal.ZERO;

    @NotNull(message = "Vui lòng nhập % phí COD")
    @DecimalMin(value = "0", message = "Phí COD không được âm")
    @DecimalMax(value = "100", message = "Phí COD không vượt quá 100%")
    private BigDecimal codFeePercent = new BigDecimal("1.00");

    @NotNull(message = "Nhập số ngày giao tối thiểu")
    @Min(0)
    private Integer estimatedDaysMin = 1;

    @NotNull(message = "Nhập số ngày giao tối đa")
    @Min(1)
    private Integer estimatedDaysMax = 3;
}
