package com.rewin.itsm.dto.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param comment  the body. {@code @NotBlank} rejects an empty string and a
 *                 whitespace-only one, which is the case that would otherwise
 *                 slip through as a valid-looking blank row.
 * @param internal agent-only note. Ignored when the author is an employee.
 */
public record CreateCommentRequest(
    @NotBlank(message = "Comment cannot be empty")
    @Size(max = 4000, message = "Comment must be at most 4000 characters")
    String comment,

    boolean internal) {
}
