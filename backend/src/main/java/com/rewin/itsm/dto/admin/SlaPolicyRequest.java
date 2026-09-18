package com.rewin.itsm.dto.admin;

import com.rewin.itsm.domain.enums.Priority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SlaPolicyRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 80, message = "Name must be at most 80 characters")
    String name,

    @NotNull(message = "Priority is required")
    Priority priority,

    @Min(value = 1, message = "Response time must be at least 1 minute")
    int responseTimeMinutes,

    @Min(value = 1, message = "Resolution time must be at least 1 minute")
    int resolutionTimeMinutes,

    boolean active) {
}
