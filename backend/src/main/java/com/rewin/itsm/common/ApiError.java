package com.rewin.itsm.common;

import java.time.Instant;
import java.util.Map;

/** The single error shape every endpoint returns. */
public record ApiError(Instant timestamp, int status, String error, String message, Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, null);
    }
}
