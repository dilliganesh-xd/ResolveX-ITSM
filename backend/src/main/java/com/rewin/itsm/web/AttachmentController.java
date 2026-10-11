package com.rewin.itsm.web;

import com.rewin.itsm.dto.incident.AttachmentResponse;
import com.rewin.itsm.security.AppUserPrincipal;
import com.rewin.itsm.service.AttachmentService;
import com.rewin.itsm.service.AttachmentService.AttachmentDownload;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Incident attachments.
 *
 * <p>Whoever may view the incident may list, download and add attachments;
 * AttachmentService enforces that per incident. Removing one is admin-only.
 */
@RestController
@RequestMapping("/api/incidents/{incidentId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public List<AttachmentResponse> list(@PathVariable UUID incidentId,
                                         @AuthenticationPrincipal AppUserPrincipal principal) {
        return attachmentService.list(incidentId, principal);
    }

    /** multipart/form-data with the file in a part named {@code file}. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(@PathVariable UUID incidentId,
                                                     @RequestParam("file") MultipartFile file,
                                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.upload(incidentId, file, principal));
    }

    /**
     * Streams the file through the backend after the access check. Always sent
     * as a download, never rendered inline, and with sniffing disabled, so a
     * file can never run as a page on this site.
     */
    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID incidentId,
                                                        @PathVariable UUID attachmentId,
                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        AttachmentDownload file = attachmentService.download(incidentId, attachmentId, principal);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .contentLength(file.sizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore())
            .body(new InputStreamResource(file.content()));
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable UUID incidentId,
                                       @PathVariable UUID attachmentId,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        attachmentService.remove(incidentId, attachmentId, principal);
        return ResponseEntity.noContent().build();
    }
}
