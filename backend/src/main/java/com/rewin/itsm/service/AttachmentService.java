package com.rewin.itsm.service;

import com.rewin.itsm.common.BadRequestException;
import com.rewin.itsm.common.NotFoundException;
import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.incident.IncidentAttachment;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.dto.incident.AttachmentResponse;
import com.rewin.itsm.repository.IncidentAttachmentRepository;
import com.rewin.itsm.repository.UserRepository;
import com.rewin.itsm.security.AppUserPrincipal;
import com.rewin.itsm.service.AttachmentFileTypes.FileType;
import com.rewin.itsm.storage.AttachmentProperties;
import com.rewin.itsm.storage.AttachmentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Files attached to incidents: contents in object storage (S3), metadata in the
 * database.
 *
 * <p>Access follows the incident exactly: whoever may view an incident may list,
 * download and add its attachments, through
 * {@link IncidentService#requireViewable}. Removing one is admin-only.
 *
 * <p>Storage and database cannot share a transaction, so the order of the two
 * writes is chosen so that a failure between them never leaves a row pointing
 * at a missing file:
 * <ul>
 *   <li>Upload stores the file first, then commits the row. If the row cannot be
 *       saved, the file just stored is deleted again.</li>
 *   <li>Remove deletes the row first, then the file. If the file cannot be
 *       deleted, it is left unreachable - nothing points to it - and logged.</li>
 * </ul>
 * Transactions are run explicitly with {@link TransactionTemplate} so the
 * storage call stays outside them: a slow upload never holds a database
 * connection.
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final IncidentService incidentService;
    private final IncidentAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final IncidentAuditService audit;
    private final AttachmentStorage storage;
    private final AttachmentProperties properties;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public AttachmentService(IncidentService incidentService, IncidentAttachmentRepository attachmentRepository,
                             UserRepository userRepository, IncidentAuditService audit, AttachmentStorage storage,
                             AttachmentProperties properties, PlatformTransactionManager transactionManager) {
        this.incidentService = incidentService;
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.audit = audit;
        this.storage = storage;
        this.properties = properties;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    /** A file ready to stream to the caller, who must close {@code content}. */
    public record AttachmentDownload(String fileName, String contentType, long sizeBytes, InputStream content) {
    }

    /** Metadata copied out of the transaction, so nothing lazy is touched after it ends. */
    private record StoredFile(String objectKey, String fileName, String contentType, long sizeBytes) {
    }

    // ---------------------------------------------------------------- reads

    public List<AttachmentResponse> list(UUID incidentId, AppUserPrincipal principal) {
        return readTransaction.execute(status -> {
            incidentService.requireViewable(incidentId, principal);
            return attachmentRepository.findAllByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                .map(AttachmentResponse::from)
                .toList();
        });
    }

    public AttachmentDownload download(UUID incidentId, UUID attachmentId, AppUserPrincipal principal) {
        StoredFile file = readTransaction.execute(status -> {
            incidentService.requireViewable(incidentId, principal);
            IncidentAttachment attachment = requireAttachment(incidentId, attachmentId);
            return new StoredFile(attachment.getObjectKey(), attachment.getOriginalFilename(),
                attachment.getContentType(), attachment.getSizeBytes());
        });
        return new AttachmentDownload(file.fileName(), file.contentType(), file.sizeBytes(),
            storage.open(file.objectKey()));
    }

    // --------------------------------------------------------------- writes

    public AttachmentResponse upload(UUID incidentId, MultipartFile file, AppUserPrincipal principal) {
        // Who may attach is settled before the file is even looked at.
        readTransaction.executeWithoutResult(status ->
            requireOpen(incidentService.requireViewable(incidentId, principal)));
        FileType type = validate(file);

        String key = newObjectKey(incidentId, type.extension());
        store(key, file, type.contentType());

        try {
            return writeTransaction.execute(status -> {
                // Checked again: the incident may have been closed while the file uploaded.
                Incident incident = requireOpen(incidentService.requireViewable(incidentId, principal));
                User uploader = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new NotFoundException("User not found"));
                IncidentAttachment saved = attachmentRepository.save(new IncidentAttachment(
                    incident, uploader, key, type.fileName(), type.contentType(), file.getSize()));
                audit.attachmentAdded(incident, uploader, type.fileName());
                return AttachmentResponse.from(saved);
            });
        } catch (RuntimeException failure) {
            // No row will point at the file, so it must not stay in storage.
            deleteQuietly(key);
            throw failure;
        }
    }

    public void remove(UUID incidentId, UUID attachmentId, AppUserPrincipal principal) {
        String key = writeTransaction.execute(status -> {
            Incident incident = incidentService.requireViewable(incidentId, principal);
            IncidentAttachment attachment = requireAttachment(incidentId, attachmentId);
            User actor = userRepository.findById(principal.getId()).orElse(null);
            attachmentRepository.delete(attachment);
            audit.attachmentRemoved(incident, actor, attachment.getOriginalFilename());
            return attachment.getObjectKey();
        });
        deleteQuietly(key);
    }

    // -------------------------------------------------------------- helpers

    private FileType validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a file to attach. Empty files cannot be attached.");
        }
        long maxBytes = properties.maxFileSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("Files can be at most " + describeSize(maxBytes) + ".");
        }
        FileType type = AttachmentFileTypes.fromFileName(file.getOriginalFilename());
        try (InputStream content = file.getInputStream()) {
            AttachmentFileTypes.requireContentMatches(type, content.readNBytes(AttachmentFileTypes.SNIFF_BYTES));
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file could not be read.");
        }
        return type;
    }

    private void store(String key, MultipartFile file, String contentType) {
        InputStream content;
        try {
            content = file.getInputStream();
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file could not be read.");
        }
        try (content) {
            storage.put(key, content, file.getSize(), contentType);
        } catch (IOException closeFailure) {
            // Only closing the uploaded temp file failed; the file was stored.
            log.debug("Could not close the uploaded file stream", closeFailure);
        }
    }

    /**
     * {@code incidents/<incident id>/<random uuid>.<ext>}. Random rather than the
     * uploaded name, so names never collide and never reach the storage path.
     */
    private String newObjectKey(UUID incidentId, String extension) {
        return properties.s3().keyPrefix() + incidentId + "/" + UUID.randomUUID() + "." + extension;
    }

    private Incident requireOpen(Incident incident) {
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new BadRequestException("This incident is closed. Files can no longer be attached.");
        }
        return incident;
    }

    private IncidentAttachment requireAttachment(UUID incidentId, UUID attachmentId) {
        return attachmentRepository.findByIdAndIncidentId(attachmentId, incidentId)
            .orElseThrow(() -> new NotFoundException("Attachment not found"));
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete stored attachment {}; nothing references it and it can be removed by hand",
                key, e);
        }
    }

    private static String describeSize(long bytes) {
        long mb = 1024 * 1024;
        return bytes >= mb && bytes % mb == 0 ? bytes / mb + " MB" : bytes / 1024 + " KB";
    }
}
