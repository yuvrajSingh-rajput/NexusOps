package com.nexusops.identity.api;

import com.nexusops.identity.application.AuthService;
import com.nexusops.identity.application.dto.*;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.shared.api.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "nexusops_refresh_token";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-org")
    public ResponseEntity<ApiResponse<AuthResponseDto>> registerOrganization(
            @Valid @RequestBody RegisterOrgRequestDto dto,
            HttpServletResponse response) {
        AuthResponseDto result = authService.registerOrganization(dto);
        addRefreshCookie(response, result.refreshToken(), 7 * 24 * 60 * 60);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(
            @Valid @RequestBody LoginRequestDto dto,
            HttpServletResponse response) {
        AuthResponseDto result = authService.login(dto);
        addRefreshCookie(response, result.refreshToken(), 7 * 24 * 60 * 60);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponseDto>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        String rawToken = extractRefreshCookie(request);
        AuthResponseDto result = authService.refreshToken(rawToken);
        addRefreshCookie(response, result.refreshToken(), 7 * 24 * 60 * 60);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String rawToken = extractRefreshCookie(request);
        authService.logout(rawToken);
        clearRefreshCookie(response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponseDto>> getMe(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserMeResponseDto result = authService.getMe(principal.getUserId(), principal.getOrganizationId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/switch-org")
    public ResponseEntity<ApiResponse<AuthResponseDto>> switchOrganization(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SwitchOrgRequestDto dto,
            HttpServletResponse response) {
        AuthResponseDto result = authService.switchOrganization(principal.getUserId(), dto.targetOrganizationId());
        addRefreshCookie(response, result.refreshToken(), 7 * 24 * 60 * 60);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken, int maxAgeSeconds) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
