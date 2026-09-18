package com.rewin.itsm.common;

/** Maps to 400 - a semantically invalid request, e.g. an illegal state transition. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
