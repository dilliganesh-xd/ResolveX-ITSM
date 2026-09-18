package com.rewin.itsm.dto.incident;

import com.rewin.itsm.domain.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 160, message = "Title must be at most 160 characters")
    String title,

    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description must be at most 5000 characters")
    String description,

    @NotBlank(message = "Category is required")
    @Size(max = 60, message = "Category must be at most 60 characters")
    String category,

    @Size(max = 60, message = "Subcategory must be at most 60 characters")
    String subcategory,

    @NotNull(message = "Priority is required")
    Priority priority) {
}
