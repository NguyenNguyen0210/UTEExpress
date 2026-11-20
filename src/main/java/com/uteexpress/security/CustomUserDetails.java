package com.uteexpress.security;

import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String fullName;
    private final String password;
    private final Role role;
    private final UserStatus status;
    private final Long hubId;
    private final String hubCode;
    private final String hubName;

    public CustomUserDetails(Long id,
                             String email,
                             String fullName,
                             String password,
                             Role role,
                             UserStatus status,
                             Long hubId,
                             String hubCode,
                             String hubName) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.password = password;
        this.role = role;
        this.status = status;
        this.hubId = hubId;
        this.hubCode = hubCode;
        this.hubName = hubName;
    }

    public static CustomUserDetails from(User user) {
        Long hubId = user.getHub() != null ? user.getHub().getId() : null;
        String hubCode = user.getHub() != null ? user.getHub().getCode() : null;
        String hubName = user.getHub() != null ? user.getHub().getName() : null;
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPasswordHash(),
                user.getRole(),
                user.getStatus(),
                hubId,
                hubCode,
                hubName
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
