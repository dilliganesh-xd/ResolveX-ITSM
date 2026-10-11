package com.rewin.itsm.storage;

import java.io.InputStream;

/**
 * Where attachment file contents are kept. The database holds only metadata.
 *
 * <p>{@link S3AttachmentStorage} is used when deployed; {@link LocalAttachmentStorage}
 * keeps files in a local folder for development and tests. Which one is active
 * is chosen by {@code app.attachments.storage} - see AttachmentStorageConfig.
 *
 * <p>Every method reports a storage-side failure as
 * {@link AttachmentStorageException}, so callers never deal with AWS SDK types.
 */
public interface AttachmentStorage {

    /** Stores {@code size} bytes read from {@code content} under {@code key}. */
    void put(String key, InputStream content, long size, String contentType);

    /**
     * Opens the stored file. The caller must close the stream.
     *
     * @throws com.rewin.itsm.common.NotFoundException if nothing is stored under the key
     */
    InputStream open(String key);

    /** Removes the file. Removing a key that does not exist is not an error. */
    void delete(String key);
}
