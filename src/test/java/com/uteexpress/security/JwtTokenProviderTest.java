package com.uteexpress.security;

import com.uteexpress.entity.Hub;
import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "uteexpress-unit-test-secret-key-at-least-32-bytes-long";
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3600000L, "ACCESS_TOKEN");
    }

    @Test
    void shouldGenerateAndParseClaimsForStaffWithHubId() {
        Hub hub = Hub.builder().id(2L).code("HCM-TD01").name("Bưu cục Thủ Đức").build();
        User staff = User.builder()
                .id(10L)
                .email("staff.hcm@uteexpress.vn")
                .fullName("Thu Hà")
                .role(Role.STAFF)
                .status(UserStatus.ACTIVE)
                .hub(hub)
                .build();

        String token = jwtTokenProvider.generateToken(staff);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getEmail(token)).isEqualTo("staff.hcm@uteexpress.vn");
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(10L);
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo("STAFF");
        assertThat(jwtTokenProvider.getHubId(token)).isEqualTo(2L);
    }

    @Test
    void shouldGenerateAndParseClaimsForCustomerWithoutHubId() {
        User customer = User.builder()
                .id(6L)
                .email("shop@uteexpress.vn")
                .fullName("Shop Hoàng Nam")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        String token = jwtTokenProvider.generateToken(customer);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getEmail(token)).isEqualTo("shop@uteexpress.vn");
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(6L);
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo("CUSTOMER");
        assertThat(jwtTokenProvider.getHubId(token)).isNull();
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1000L, "ACCESS_TOKEN");
        User admin = User.builder()
                .id(1L)
                .email("admin@uteexpress.vn")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        String expiredToken = expiredProvider.generateToken(admin);

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void shouldRejectTokenSignedWithDifferentSecret() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "another-completely-different-secret-key-at-least-32-chars", 3600000L, "ACCESS_TOKEN");
        User user = User.builder()
                .id(1L)
                .email("admin@uteexpress.vn")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        String forgedToken = otherProvider.generateToken(user);

        assertThat(jwtTokenProvider.validateToken(forgedToken)).isFalse();
        assertThat(jwtTokenProvider.validateToken("not.a.valid.jwt")).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }
}
