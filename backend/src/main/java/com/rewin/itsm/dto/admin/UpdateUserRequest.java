package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Editing a user's profile.
 *
 * <p>There is deliberately no password field. Ordinary profile editing must not
 * be able to set or clear a credential by accident; changing a password is a
 * separate, explicit operation.
 *
 * @param teamId      the team to move the user to. Absent leaves the team alone;
 *                    {@code clearTeam} is how a team is removed, because null
 *                    cannot mean both "unchanged" and "none".
 */
public record UpdateUserRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 160, message = "Email must be at most 160 characters")
    String email,

    @NotNull(message = "Role is required")
    RoleName role,

    UUID teamId,

    boolean clearTeam,

    boolean active) {
}
