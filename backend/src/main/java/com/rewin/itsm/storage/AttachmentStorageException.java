package com.rewin.itsm.storage;

/** File storage could not be reached or refused the operation. Maps to 503. */
public class AttachmentStorageException extends RuntimeException {
    public AttachmentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
