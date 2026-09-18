package com.rewin.itsm.dto.auth;

import com.rewin.itsm.domain.enums.RoleName;
import java.util.UUID;

public record LoginResponse(
    String token,
    String tokenType,
    long expiresInSeconds,
    UUID userId,
    String name,
    String email,
    RoleName role,
    TeamSummary team) {
}
