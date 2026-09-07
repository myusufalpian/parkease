package id.xyz.parkease.controller;

import id.xyz.parkease.dto.AuthResponse;
import id.xyz.parkease.dto.LoginRequest;
import id.xyz.parkease.dto.LogoutRequest;
import id.xyz.parkease.dto.RefreshRequest;
import id.xyz.parkease.dto.RegisterRequest;
import id.xyz.parkease.service.AuthService;
import id.xyz.parkease.security.AuthRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final int USER_AGENT_MAX = 500;

    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter authRateLimiter) {
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        authRateLimiter.check("register", clientIp(servletRequest));
        AuthResponse response = authService.register(request, userAgent(servletRequest), clientIp(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        authRateLimiter.check("login", clientIp(servletRequest));
        return authService.login(request, userAgent(servletRequest), clientIp(servletRequest));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        return authService.refresh(request.refreshToken(), userAgent(servletRequest), clientIp(servletRequest));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }

    private String userAgent(HttpServletRequest servletRequest) {
        String userAgent = servletRequest.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > USER_AGENT_MAX) {
            return userAgent.substring(0, USER_AGENT_MAX);
        }
        return userAgent;
    }

    private String clientIp(HttpServletRequest servletRequest) {
        return servletRequest.getRemoteAddr();
    }
}
