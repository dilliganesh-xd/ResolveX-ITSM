package com.rewin.itsm.storage;

import com.rewin.itsm.common.NotFoundException;
import java.io.InputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Attachments in a private S3 bucket.
 *
 * <p>Objects are written without any public ACL and are never handed out as
 * URLs: the backend streams them to a caller only after the incident access
 * check has passed.
 */
public class S3AttachmentStorage implements AttachmentStorage {

    private final S3Client s3;
    private final String bucket;

    public S3AttachmentStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(size)
                    .build(),
                RequestBody.fromInputStream(content, size));
        } catch (SdkException e) {
            throw new AttachmentStorageException("Could not upload the file to storage.", e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new NotFoundException("The attachment's file is missing from storage.");
        } catch (SdkException e) {
            throw new AttachmentStorageException("Could not read the file from storage.", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new AttachmentStorageException("Could not delete the file from storage.", e);
        }
    }
}
