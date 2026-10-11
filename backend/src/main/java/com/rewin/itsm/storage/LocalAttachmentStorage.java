package com.rewin.itsm.storage;

import com.rewin.itsm.common.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Attachments in a folder on disk, for local development and tests, so neither
 * needs an AWS account. Never used when deployed.
 */
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path root;

    public LocalAttachmentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not write the file to local storage.", e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new NotFoundException("The attachment's file is missing from storage.");
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not read the file from local storage.", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not delete the file from local storage.", e);
        }
    }

    /** Keys are server-generated, but a key must still never reach outside the root. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
