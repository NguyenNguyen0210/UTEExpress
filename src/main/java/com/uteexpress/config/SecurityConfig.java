package com.uteexpress.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uteexpress.dto.ApiErrorResponse;
import com.uteexpress.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/tracking/**", "/estimate/**", "/hubs/**",
                                "/auth/**", "/payment/vnpay-return",
                                "/api/v1/auth/login", "/api/v1/public/**", "/api/v1/payment/vnpay-ipn",
                                "/css/**", "/js/**", "/images/**", "/ws/**",
                                "/error", "/error/**", "/favicon.ico"
                        ).permitAll()
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/shipper/**").hasRole("SHIPPER")
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/notifications/**", "/api/v1/chat/**",
                                "/shipments/*/waybill", "/files/pod/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (isApiRequest(request)) {
                                writeJsonError(response, HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc phiên làm việc đã hết hạn");
                            } else {
                                response.sendRedirect("/auth/login");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (isApiRequest(request)) {
                                writeJsonError(response, HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này");
                            } else {
                                response.setStatus(HttpStatus.FORBIDDEN.value());
                                request.getRequestDispatcher("/error/403").forward(request, response);
                            }
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/");
    }

    private void writeJsonError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse errorResponse = ApiErrorResponse.of(status.value(), status.getReasonPhrase(), message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
