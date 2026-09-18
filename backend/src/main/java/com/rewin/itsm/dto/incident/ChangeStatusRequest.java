package com.rewin.itsm.dto.incident;

import com.rewin.itsm.domain.enums.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusRequest(
    @NotNull(message = "Status is required")
    IncidentStatus status,

    @Size(max = 400, message = "Note must be at most 400 characters")
    String note) {
}
