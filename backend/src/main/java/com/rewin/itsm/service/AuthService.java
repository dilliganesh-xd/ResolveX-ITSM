package com.rewin.itsm.service;

import com.rewin.itsm.common.NotFoundException;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.dto.auth.LoginRequest;
import com.rewin.itsm.dto.auth.LoginResponse;
import com.rewin.itsm.dto.auth.TeamSummary;
import com.rewin.itsm.dto.auth.UserResponse;
import com.rewin.itsm.repository.UserRepository;
import com.rewin.itsm.security.AppUserPrincipal;
import com.rewin.itsm.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /**
     * Verifies the credentials and issues a token.
     *
     * <p>A bad password, an unknown email and a deactivated account all surface
     * as the same 401 via the global handler, so the endpoint cannot be used to
     * enumerate accounts.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
            .orElseThrow(() -> new NotFoundException("User not found"));

        return new LoginResponse(
            jwtService.issue(principal),
            "Bearer",
            jwtService.getTtlSeconds(),
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole(),
            TeamSummary.from(user.getTeam()));
    }

    /** The caller behind the current token, for GET /api/auth/me. */
    @Transactional(readOnly = true)
    public UserResponse currentUser(AppUserPrincipal principal) {
        return userRepository.findById(principal.getId())
            .map(UserResponse::from)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
