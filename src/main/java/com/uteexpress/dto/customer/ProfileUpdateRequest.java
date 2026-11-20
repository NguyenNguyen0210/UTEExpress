package com.uteexpress.dto.customer;

import com.uteexpress.entity.User;
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
public class ProfileUpdateRequest {

    @NotBlank(message = "Vui lòng nhập họ tên hoặc tên shop")
    @Size(max = 120, message = "Họ tên tối đa 120 ký tự")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^0\\d{9,10}$", message = "Số điện thoại phải bắt đầu bằng 0 và gồm 10-11 chữ số")
    private String phone;

    @Size(max = 100, message = "Tên ngân hàng tối đa 100 ký tự")
    private String bankName;

    @Size(max = 50, message = "Số tài khoản tối đa 50 ký tự")
    private String bankAccountNumber;

    @Size(max = 120, message = "Tên chủ tài khoản tối đa 120 ký tự")
    private String bankAccountHolder;

    public static ProfileUpdateRequest from(User user) {
        return ProfileUpdateRequest.builder()
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .bankName(user.getBankName())
                .bankAccountNumber(user.getBankAccountNumber())
                .bankAccountHolder(user.getBankAccountHolder())
                .build();
    }
}
