package com.rewin.itsm.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An admin-assisted password reset.
 *
 * <p>Its own request type, not a field on the profile edit, so resetting a
 * credential is always a deliberate act rather than a side effect of changing
 * somebody's name.
 */
public record ResetPasswordRequest(
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    String password) {
}
