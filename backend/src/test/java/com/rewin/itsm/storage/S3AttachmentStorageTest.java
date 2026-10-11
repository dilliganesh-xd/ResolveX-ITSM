package com.rewin.itsm.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rewin.itsm.common.NotFoundException;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** What the S3 storage asks of AWS, checked against a mocked client. */
class S3AttachmentStorageTest {

    private final S3Client s3 = mock(S3Client.class);
    private final S3AttachmentStorage storage = new S3AttachmentStorage(s3, "resolvex-itsm-attachments");

    @Test
    void putWritesToTheConfiguredBucketWithoutAPublicAcl() {
        storage.put("incidents/1/a.png", new ByteArrayInputStream(new byte[] {1, 2, 3}), 3, "image/png");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("resolvex-itsm-attachments");
        assertThat(request.getValue().key()).isEqualTo("incidents/1/a.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().contentLength()).isEqualTo(3L);
        assertThat(request.getValue().acl()).isNull();
    }

    @Test
    void anAwsFailureOnUploadBecomesAStorageException() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(SdkClientException.create("Unable to connect"));

        assertThatThrownBy(() -> storage.put("k", new ByteArrayInputStream(new byte[] {1}), 1, "text/plain"))
            .isInstanceOf(AttachmentStorageException.class);
    }

    @Test
    void aMissingObjectIsNotFound() {
        when(s3.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> storage.open("incidents/1/gone.png")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteTargetsTheConfiguredBucketAndWrapsFailures() {
        storage.delete("incidents/1/a.png");
        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("resolvex-itsm-attachments");
        assertThat(request.getValue().key()).isEqualTo("incidents/1/a.png");

        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(SdkClientException.create("Access Denied"));
        assertThatThrownBy(() -> storage.delete("k")).isInstanceOf(AttachmentStorageException.class);
    }
}
