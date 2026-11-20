package com.uteexpress.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressFormRequest {

    @NotBlank(message = "Vui lòng nhập tên gợi nhớ địa chỉ (VD: Kho Thủ Đức)")
    @Size(max = 80, message = "Tên gợi nhớ tối đa 80 ký tự")
    private String label;

    @NotBlank(message = "Vui lòng nhập tên người liên hệ")
    @Size(max = 120, message = "Tên người liên hệ tối đa 120 ký tự")
    private String contactName;

    @NotBlank(message = "Vui lòng nhập số điện thoại liên hệ")
    @Pattern(regexp = "^0\\d{9,10}$", message = "Số điện thoại phải bắt đầu bằng 0 và gồm 10-11 chữ số")
    private String contactPhone;

    @NotBlank(message = "Vui lòng chọn tỉnh/thành phố")
    @Size(max = 100, message = "Tỉnh/thành phố tối đa 100 ký tự")
    private String province;

    @NotBlank(message = "Vui lòng nhập quận/huyện")
    @Size(max = 100, message = "Quận/huyện tối đa 100 ký tự")
    private String district;

    @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết")
    @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
    private String addressDetail;

    private boolean isDefault;

    public boolean isDefault() {
        return isDefault;
    }

    public boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
