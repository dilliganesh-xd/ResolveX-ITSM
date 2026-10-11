package com.rewin.itsm.storage;

import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Picks the attachment storage from {@code app.attachments.storage}:
 * {@code s3} (the default, used when deployed) or {@code local}.
 */
@Configuration
@EnableConfigurationProperties(AttachmentProperties.class)
public class AttachmentStorageConfig {

    /**
     * The S3 client authenticates through the default AWS credential provider
     * chain. On EC2 that resolves to the instance's IAM role, so no access key
     * is configured anywhere in the application or the pipeline.
     */
    @Bean
    @ConditionalOnProperty(name = "app.attachments.storage", havingValue = "s3", matchIfMissing = true)
    public S3Client attachmentS3Client(AttachmentProperties properties) {
        return S3Client.builder()
            .region(Region.of(properties.s3().region()))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.attachments.storage", havingValue = "s3", matchIfMissing = true)
    public AttachmentStorage s3AttachmentStorage(S3Client attachmentS3Client, AttachmentProperties properties) {
        String bucket = properties.s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            // Fail at startup, not at the first upload.
            throw new IllegalStateException(
                "S3_BUCKET_NAME must be set when attachment storage is s3 (ATTACHMENT_STORAGE=s3).");
        }
        return new S3AttachmentStorage(attachmentS3Client, bucket);
    }

    @Bean
    @ConditionalOnProperty(name = "app.attachments.storage", havingValue = "local")
    public AttachmentStorage localAttachmentStorage(AttachmentProperties properties) {
        return new LocalAttachmentStorage(Path.of(properties.localDir()));
    }
}
