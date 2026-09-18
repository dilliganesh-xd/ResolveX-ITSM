package com.rewin.itsm.common;

/** Maps to 409 - e.g. an email that is already registered. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
