package com.uteexpress.dto.admin;

import com.uteexpress.enums.HubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HubFormRequest {

    private Long id;

    @NotBlank(message = "Vui lòng nhập mã bưu cục")
    @Size(max = 20, message = "Mã bưu cục tối đa 20 ký tự")
    private String code;

    @NotBlank(message = "Vui lòng nhập tên bưu cục")
    @Size(max = 150, message = "Tên bưu cục tối đa 150 ký tự")
    private String name;

    @NotNull(message = "Vui lòng chọn loại hình bưu cục")
    private HubType type = HubType.POST_OFFICE;

    @NotBlank(message = "Vui lòng nhập tỉnh/thành phố")
    @Size(max = 100)
    private String province;

    @NotBlank(message = "Vui lòng nhập quận/huyện")
    @Size(max = 100)
    private String district;

    @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết")
    @Size(max = 255)
    private String address;

    @NotBlank(message = "Vui lòng nhập số hotline")
    @Size(max = 20)
    private String phone;
}
