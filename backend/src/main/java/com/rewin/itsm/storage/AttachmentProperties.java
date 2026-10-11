package com.rewin.itsm.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * {@code app.attachments.*}. Every value is set from an environment variable in
 * application.yml; none of them is a secret.
 *
 * @param storage     {@code s3} or {@code local}
 * @param maxFileSize largest file accepted, e.g. {@code 10MB}
 * @param localDir    folder used by the {@code local} storage
 * @param s3          bucket settings used by the {@code s3} storage
 */
@ConfigurationProperties("app.attachments")
public record AttachmentProperties(String storage, DataSize maxFileSize, String localDir, S3 s3) {

    /**
     * @param bucket    private bucket name ({@code S3_BUCKET_NAME})
     * @param region    bucket region ({@code AWS_REGION})
     * @param keyPrefix folder-like prefix for every object key
     */
    public record S3(String bucket, String region, String keyPrefix) {
    }
}
