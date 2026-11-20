package com.uteexpress.service;

import com.uteexpress.dto.admin.CreateUserRequest;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.UserStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.HubRepository;
import com.uteexpress.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final HubRepository hubRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> searchUsers(Role role, Long hubId, String keyword) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (hubId != null) {
                predicates.add(cb.equal(root.get("hub").get("id"), hubId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("phone")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "id"));
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với ID: " + id));
    }

    @Transactional
    public User createStaffOrShipper(CreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email '" + email + "' đã tồn tại");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException("Số điện thoại '" + phone + "' đã tồn tại");
        }

        Hub hub = null;
        if (request.getRole() == Role.STAFF || request.getRole() == Role.SHIPPER) {
            if (request.getHubId() == null) {
                throw new BusinessException("Tài khoản Nhân viên Bưu cục (STAFF) và Giao nhận (SHIPPER) bắt buộc phải gắn vào một Bưu cục");
            }
            hub = hubRepository.findById(request.getHubId())
                    .orElseThrow(() -> new BusinessException("Bưu cục được chọn không tồn tại"));
            if (!Boolean.TRUE.equals(hub.getActive())) {
                throw new BusinessException("Không thể gán nhân sự vào bưu cục đang tạm ngưng hoạt động");
            }
        }

        User user = User.builder()
                .email(email)
                .phone(phone)
                .fullName(request.getFullName().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .hub(hub)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User toggleStatus(Long targetUserId, Long currentAdminId) {
        if (targetUserId.equals(currentAdminId)) {
            throw new BusinessException("Không thể tự khóa tài khoản Quản trị viên đang đăng nhập");
        }
        User user = getById(targetUserId);
        user.setStatus(user.getStatus() == UserStatus.ACTIVE ? UserStatus.LOCKED : UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
