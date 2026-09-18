package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUserRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 160, message = "Email must be at most 160 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    String password,

    @NotNull(message = "Role is required")
    RoleName role,

    UUID teamId) {
}
