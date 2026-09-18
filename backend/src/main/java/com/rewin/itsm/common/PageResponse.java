package com.rewin.itsm.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The envelope every collection endpoint returns.
 *
 * <p>V1 does not paginate the comment or activity lists in practice - they are
 * small - but they are served through this shape from the start, so switching to
 * real paging later is a server-side change and not a breaking change to every
 * caller.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
