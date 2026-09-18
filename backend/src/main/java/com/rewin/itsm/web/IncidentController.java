package com.rewin.itsm.web;

import com.rewin.itsm.common.PageResponse;
import com.rewin.itsm.dto.incident.ActivityResponse;
import com.rewin.itsm.dto.incident.AssignIncidentRequest;
import com.rewin.itsm.dto.incident.ChangeStatusRequest;
import com.rewin.itsm.dto.incident.CommentResponse;
import com.rewin.itsm.dto.incident.CreateCommentRequest;
import com.rewin.itsm.dto.incident.CloseIncidentRequest;
import com.rewin.itsm.dto.incident.CreateIncidentRequest;
import com.rewin.itsm.dto.incident.IncidentFilter;
import com.rewin.itsm.dto.incident.ResolveIncidentRequest;
import com.rewin.itsm.dto.incident.IncidentDetailResponse;
import com.rewin.itsm.dto.incident.IncidentSummaryResponse;
import com.rewin.itsm.dto.incident.IncidentSummaryStats;
import com.rewin.itsm.dto.incident.UpdateIncidentRequest;
import com.rewin.itsm.security.AppUserPrincipal;
import com.rewin.itsm.service.IncidentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incident API.
 *
 * <p>The {@code @PreAuthorize} annotations here are the coarse role gate. Which
 * rows a caller may see or change is decided in IncidentService, because it
 * depends on the incident, not on the endpoint.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /**
     * Employee: own incidents. Agent: their queue. Admin: everything.
     * Optional filters narrow that scope; they can never widen it.
     */
    @GetMapping
    public PageResponse<IncidentSummaryResponse> list(
            IncidentFilter filter,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.list(filter, pageable, principal);
    }

    /** Dashboard counts for the caller, scoped exactly like the list above. */
    @GetMapping("/summary")
    public IncidentSummaryStats summary(@AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.summary(principal);
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse get(@PathVariable UUID id,
                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.get(id, principal);
    }

    @PostMapping
    public ResponseEntity<IncidentDetailResponse> create(
            @Valid @RequestBody CreateIncidentRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        IncidentDetailResponse created = incidentService.create(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','ADMIN')")
    public IncidentDetailResponse update(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateIncidentRequest request,
                                         @AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.update(id, request, principal);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','ADMIN')")
    public IncidentDetailResponse changeStatus(@PathVariable UUID id,
                                               @Valid @RequestBody ChangeStatusRequest request,
                                               @AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.changeStatus(id, request, principal);
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','ADMIN')")
    public IncidentDetailResponse assign(@PathVariable UUID id,
                                         @RequestBody AssignIncidentRequest request,
                                         @AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.assign(id, request, principal);
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','ADMIN')")
    public IncidentDetailResponse resolve(@PathVariable UUID id,
                                          @Valid @RequestBody ResolveIncidentRequest request,
                                          @AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.resolve(id, request, principal);
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','ADMIN')")
    public IncidentDetailResponse close(@PathVariable UUID id,
                                        @Valid @RequestBody(required = false) CloseIncidentRequest request,
                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.close(id, request, principal);
    }

    /**
     * The comment thread. An employee sees only the public comments on their own
     * incident; an agent or admin sees internal notes too.
     */
    @GetMapping("/{id}/comments")
    public PageResponse<CommentResponse> listComments(
            @PathVariable UUID id,
            @PageableDefault(size = 100) Pageable pageable,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.listComments(id, pageable, principal);
    }

    /** The audit trail, newest first. There is no write or delete counterpart. */
    @GetMapping("/{id}/activities")
    public PageResponse<ActivityResponse> listActivities(
            @PathVariable UUID id,
            @PageableDefault(size = 200) Pageable pageable,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return incidentService.listActivities(id, pageable, principal);
    }

    /** Any role may comment on an incident they are allowed to see. */
    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        CommentResponse created = incidentService.addComment(id, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
