package com.rewin.itsm.dto.incident;

import jakarta.validation.constraints.Size;

public record CloseIncidentRequest(
    @Size(max = 400, message = "Note must be at most 400 characters")
    String note) {
}
