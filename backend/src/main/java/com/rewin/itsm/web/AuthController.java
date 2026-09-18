package com.rewin.itsm.web;

import com.rewin.itsm.dto.auth.LoginRequest;
import com.rewin.itsm.dto.auth.LoginResponse;
import com.rewin.itsm.dto.auth.UserResponse;
import com.rewin.itsm.security.AppUserPrincipal;
import com.rewin.itsm.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Lets the frontend restore a session from a stored token without a re-login. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return authService.currentUser(principal);
    }
}
