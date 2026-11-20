package com.uteexpress.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Vui lòng nhập họ tên hoặc tên shop")
    @Size(max = 120, message = "Họ tên tối đa 120 ký tự")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^0\\d{9,10}$", message = "Số điện thoại phải bắt đầu bằng 0 và gồm 10-11 chữ số")
    private String phone;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(min = 6, max = 100, message = "Mật khẩu tối thiểu 6 ký tự")
    private String password;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu")
    private String confirmPassword;
}
