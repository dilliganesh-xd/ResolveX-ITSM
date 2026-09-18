package com.rewin.itsm.dto.incident;

import com.rewin.itsm.domain.enums.Priority;
import jakarta.validation.constraints.Size;

/** Agent/admin edit. Null fields are left unchanged. */
public record UpdateIncidentRequest(
    @Size(max = 160, message = "Title must be at most 160 characters")
    String title,

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    String description,

    @Size(max = 60, message = "Category must be at most 60 characters")
    String category,

    @Size(max = 60, message = "Subcategory must be at most 60 characters")
    String subcategory,

    Priority priority) {
}
