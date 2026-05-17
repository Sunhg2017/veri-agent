package com.songhg.veri.agent.auth.api;

import com.songhg.veri.agent.auth.application.AuthService;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthorizationService authorizationService;

    public AuthController(
            AuthService authService,
            AuthorizationService authorizationService
    ) {
        this.authService = authService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public LogoutResponse logout(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @Valid @RequestBody(required = false) LogoutRequest request
    ) {
        return authService.logout(principal, request);
    }

    @PostMapping("/change-password")
    public ChangePasswordResponse changePassword(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return authService.changePassword(principal, request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return CurrentUserResponse.from(principal, authorizationService.permissions(principal));
    }
}
