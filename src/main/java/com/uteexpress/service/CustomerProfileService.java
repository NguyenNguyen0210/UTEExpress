package com.uteexpress.service;

import com.uteexpress.dto.customer.ProfileUpdateRequest;
import com.uteexpress.entity.User;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getProfile(Long customerId) {
        return userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản khách hàng"));
    }

    @Transactional
    public User updateProfile(Long customerId, ProfileUpdateRequest request) {
        User user = getProfile(customerId);
        String normalizedPhone = request.getPhone().trim();

        if (!normalizedPhone.equals(user.getPhone()) && userRepository.existsByPhone(normalizedPhone)) {
            throw new BusinessException("Số điện thoại '" + normalizedPhone + "' đã được sử dụng bởi tài khoản khác");
        }

        user.setFullName(request.getFullName().trim());
        user.setPhone(normalizedPhone);
        user.setBankName(normalizeOptional(request.getBankName()));
        user.setBankAccountNumber(normalizeOptional(request.getBankAccountNumber()));
        user.setBankAccountHolder(normalizeOptional(request.getBankAccountHolder()));

        return userRepository.save(user);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
