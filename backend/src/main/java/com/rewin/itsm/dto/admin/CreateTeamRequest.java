package com.rewin.itsm.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 80, message = "Name must be at most 80 characters")
    String name,

    @Size(max = 240, message = "Description must be at most 240 characters")
    String description) {
}
