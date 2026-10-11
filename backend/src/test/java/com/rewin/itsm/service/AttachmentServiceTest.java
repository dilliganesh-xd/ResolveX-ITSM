package com.rewin.itsm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.incident.IncidentAttachment;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.IncidentAttachmentRepository;
import com.rewin.itsm.repository.UserRepository;
import com.rewin.itsm.security.AppUserPrincipal;
import com.rewin.itsm.storage.AttachmentProperties;
import com.rewin.itsm.storage.AttachmentStorage;
import com.rewin.itsm.storage.AttachmentStorageException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.util.unit.DataSize;

/**
 * The two-system failure cases, which an integration test cannot easily
 * provoke: storage and database fail independently, and neither failure may
 * leave the other holding something the first does not.
 */
class AttachmentServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private final IncidentService incidentService = mock(IncidentService.class);
    private final IncidentAttachmentRepository attachmentRepository = mock(IncidentAttachmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final IncidentAuditService audit = mock(IncidentAuditService.class);
    private final AttachmentStorage storage = mock(AttachmentStorage.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private final UUID incidentId = UUID.randomUUID();
    private final AppUserPrincipal principal = mock(AppUserPrincipal.class);
    private AttachmentService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Incident incident = mock(Incident.class);
        when(incident.getStatus()).thenReturn(IncidentStatus.IN_PROGRESS);
        when(incidentService.requireViewable(incidentId, principal)).thenReturn(incident);
        UUID userId = UUID.randomUUID();
        when(principal.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mock(User.class)));

        AttachmentProperties properties = new AttachmentProperties("local", DataSize.ofMegabytes(10), "unused",
            new AttachmentProperties.S3("bucket", "ap-south-1", "incidents/"));
        service = new AttachmentService(incidentService, attachmentRepository, userRepository, audit, storage,
            properties, transactionManager);
    }

    @Test
    void whenTheMetadataCannotBeSavedTheUploadedFileIsDeletedAgain() {
        when(attachmentRepository.save(any(IncidentAttachment.class)))
            .thenThrow(new DataIntegrityViolationException("database said no"));

        assertThatThrownBy(() -> service.upload(incidentId, png(), principal))
            .isInstanceOf(DataIntegrityViolationException.class);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(storage).put(stored.capture(), any(InputStream.class), eq((long) PNG.length), eq("image/png"));
        verify(storage).delete(stored.getValue());
    }

    @Test
    void whenTheCommitFailsTheUploadedFileIsDeletedAgain() {
        when(attachmentRepository.save(any(IncidentAttachment.class))).thenAnswer(call -> call.getArgument(0));
        // The first commit is the read-only access check; the second is the metadata write.
        doNothing().doThrow(new TransactionSystemException("commit failed"))
            .when(transactionManager).commit(any());

        assertThatThrownBy(() -> service.upload(incidentId, png(), principal))
            .isInstanceOf(TransactionSystemException.class);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(storage).put(stored.capture(), any(InputStream.class), anyLong(), anyString());
        verify(storage).delete(stored.getValue());
    }

    @Test
    void whenStorageRejectsTheUploadNoMetadataIsSaved() {
        doThrow(new AttachmentStorageException("S3 down", null))
            .when(storage).put(anyString(), any(InputStream.class), anyLong(), anyString());

        assertThatThrownBy(() -> service.upload(incidentId, png(), principal))
            .isInstanceOf(AttachmentStorageException.class);

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void anInvalidFileNeverReachesStorage() {
        MockMultipartFile fake = new MockMultipartFile("file", "photo.png", "image/png", "text".getBytes());

        assertThatThrownBy(() -> service.upload(incidentId, fake, principal))
            .hasMessageContaining("do not match");

        verify(storage, never()).put(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void removingStillSucceedsWhenTheStoredFileCannotBeDeleted() {
        UUID attachmentId = UUID.randomUUID();
        IncidentAttachment attachment = mock(IncidentAttachment.class);
        when(attachment.getObjectKey()).thenReturn("incidents/x/y.png");
        when(attachment.getOriginalFilename()).thenReturn("y.png");
        when(attachmentRepository.findByIdAndIncidentId(attachmentId, incidentId)).thenReturn(Optional.of(attachment));
        doThrow(new AttachmentStorageException("S3 down", null)).when(storage).delete("incidents/x/y.png");

        service.remove(incidentId, attachmentId, principal);

        // The row is gone; the leftover object is unreachable and only logged.
        verify(attachmentRepository).delete(attachment);
        verify(storage).delete("incidents/x/y.png");
    }

    @Test
    void theStorageKeyIsScopedToTheIncidentAndUnique() {
        when(attachmentRepository.save(any(IncidentAttachment.class))).thenAnswer(call -> call.getArgument(0));
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);

        service.upload(incidentId, png(), principal);
        service.upload(incidentId, png(), principal);

        verify(storage, times(2)).put(keys.capture(), any(InputStream.class), anyLong(), anyString());
        assertThat(keys.getAllValues()).doesNotHaveDuplicates()
            .allMatch(key -> key.matches("incidents/" + incidentId + "/[0-9a-f-]{36}\\.png"));
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "screen.png", "image/png", PNG);
    }
}
