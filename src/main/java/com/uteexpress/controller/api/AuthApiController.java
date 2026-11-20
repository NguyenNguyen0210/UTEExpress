package com.uteexpress.controller.api;

import com.uteexpress.dto.auth.AuthTokenResponse;
import com.uteexpress.dto.auth.LoginRequest;
import com.uteexpress.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                   HttpServletResponse response) {
        AuthTokenResponse auth = authService.authenticate(request);
        response.addHeader(HttpHeaders.SET_COOKIE, authService.buildAuthCookie(auth.accessToken()).toString());
        return ResponseEntity.ok(auth);
    }
}
