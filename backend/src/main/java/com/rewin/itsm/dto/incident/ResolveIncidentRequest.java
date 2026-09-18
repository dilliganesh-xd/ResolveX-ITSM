package com.rewin.itsm.dto.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Resolving requires an explanation; that is the point of the endpoint. */
public record ResolveIncidentRequest(
    @NotBlank(message = "Resolution summary is required")
    @Size(max = 4000, message = "Resolution summary must be at most 4000 characters")
    String resolutionSummary) {
}
