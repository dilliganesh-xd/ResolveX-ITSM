package com.rewin.itsm.service;

import com.rewin.itsm.common.BadRequestException;
import com.rewin.itsm.common.PageResponse;
import com.rewin.itsm.common.NotFoundException;
import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.SlaStatus;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.incident.IncidentComment;
import com.rewin.itsm.domain.team.Team;
import com.rewin.itsm.domain.user.User;
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
import com.rewin.itsm.repository.IncidentActivityRepository;
import com.rewin.itsm.repository.IncidentCommentRepository;
import com.rewin.itsm.repository.IncidentRepository;
import com.rewin.itsm.repository.IncidentSpecifications;
import com.rewin.itsm.repository.TeamRepository;
import com.rewin.itsm.repository.UserRepository;
import com.rewin.itsm.security.AppUserPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Incident use cases, and the place where per-row authorization lives.
 *
 * <p>Role gates in SecurityConfig can only say that an employee may call an
 * endpoint. Whether this employee may touch this incident depends on the row,
 * so every read and write passes through {@link #assertCanView} or
 * {@link #assertCanAct}.
 */
@Service
public class IncidentService {

    /**
     * Legal transitions for the generic status endpoint.
     *
     * <p>RESOLVED and CLOSED are deliberately absent as targets here: resolving
     * requires a summary and closing has its own rule, so both have dedicated
     * endpoints and this map would let a caller reach them without either.
     * Reopening a resolved incident back to IN_PROGRESS is allowed.
     */
    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = Map.of(
        IncidentStatus.NEW, Set.of(IncidentStatus.ASSIGNED, IncidentStatus.IN_PROGRESS),
        IncidentStatus.ASSIGNED, Set.of(IncidentStatus.IN_PROGRESS),
        IncidentStatus.IN_PROGRESS, Set.of(IncidentStatus.ASSIGNED),
        IncidentStatus.RESOLVED, Set.of(IncidentStatus.IN_PROGRESS),
        IncidentStatus.CLOSED, Set.of());

    private final IncidentRepository incidentRepository;
    private final IncidentCommentRepository commentRepository;
    private final IncidentActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final SlaService slaService;
    private final IncidentAuditService audit;
    private final NotificationService notifications;

    public IncidentService(IncidentRepository incidentRepository, IncidentCommentRepository commentRepository,
                           IncidentActivityRepository activityRepository, UserRepository userRepository,
                           TeamRepository teamRepository, SlaService slaService, IncidentAuditService audit,
                           NotificationService notifications) {
        this.incidentRepository = incidentRepository;
        this.commentRepository = commentRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.slaService = slaService;
        this.audit = audit;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- reads

    /**
     * The scoped, filtered list. The role scope is a specification like any
     * other, so a filter can never widen what a role is allowed to see - the
     * two are ANDed in a single query.
     */
    @Transactional
    public PageResponse<IncidentSummaryResponse> list(IncidentFilter filter, Pageable pageable,
                                                      AppUserPrincipal principal) {
        Specification<Incident> spec = scopeFor(principal);

        if (filter != null) {
            if (filter.status() != null) {
                spec = and(spec, IncidentSpecifications.hasStatus(filter.status()));
            }
            if (filter.priority() != null) {
                spec = and(spec, IncidentSpecifications.hasPriority(filter.priority()));
            }
            if (filter.teamId() != null) {
                spec = and(spec, IncidentSpecifications.hasTeam(filter.teamId()));
            }
            if (filter.slaStatus() != null) {
                spec = and(spec, IncidentSpecifications.hasSlaStatus(filter.slaStatus()));
            }
            if (filter.hasSearch()) {
                spec = and(spec, IncidentSpecifications.matches(filter.search().trim()));
            }
        }

        // Newest first unless the caller asked otherwise, so a queue opens on
        // what just arrived rather than on whatever the database returns first.
        Pageable sorted = pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Incident> page = incidentRepository.findAll(spec, sorted);
        page.forEach(this::refreshSla);
        return PageResponse.from(page.map(this::summarise));
    }

    /**
     * Dashboard counts for the caller, over the same role-scoped set the list
     * endpoint uses - so a card and the table beneath it cannot disagree.
     *
     * <p>Counted in memory over that scope, which is honest at MVP volume and
     * becomes a set of grouped queries when it is not.
     */
    @Transactional
    public IncidentSummaryStats summary(AppUserPrincipal principal) {
        List<Incident> scoped = incidentRepository.findAll(scopeFor(principal));
        scoped.forEach(this::refreshSla);

        Instant startOfToday = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant();

        return new IncidentSummaryStats(
            scoped.size(),
            scoped.stream().filter(i -> i.getStatus() == IncidentStatus.NEW
                || i.getStatus() == IncidentStatus.ASSIGNED).count(),
            scoped.stream().filter(i -> i.getStatus() == IncidentStatus.IN_PROGRESS).count(),
            scoped.stream().filter(i -> i.getStatus() == IncidentStatus.RESOLVED).count(),
            scoped.stream().filter(i -> i.getStatus() == IncidentStatus.CLOSED).count(),
            scoped.stream().filter(i -> i.getSlaStatus() == SlaStatus.ON_TRACK).count(),
            scoped.stream().filter(i -> i.getSlaStatus() == SlaStatus.AT_RISK).count(),
            scoped.stream().filter(i -> i.getSlaStatus() == SlaStatus.BREACHED).count(),
            scoped.stream().filter(i -> i.getAssignedAgent() != null
                && i.getAssignedAgent().getId().equals(principal.getId())).count(),
            scoped.stream().filter(i -> i.getAssignedAgent() == null
                && i.getStatus() != IncidentStatus.CLOSED).count(),
            scoped.stream().filter(i -> i.getResolvedAt() != null
                && i.getResolvedAt().isAfter(startOfToday)).count());
    }

    /** The role scope on its own, shared by the list and the summary. */
    private Specification<Incident> scopeFor(AppUserPrincipal principal) {
        return switch (principal.getRole()) {
            case ADMIN -> null;
            case SUPPORT_AGENT -> IncidentSpecifications.agentScope(principal.getTeamId(), principal.getId());
            case EMPLOYEE -> IncidentSpecifications.reportedBy(principal.getId());
        };
    }

    /** Attaches the per-stage SLA verdicts, which only SlaService decides. */
    private IncidentSummaryResponse summarise(Incident incident) {
        return IncidentSummaryResponse.from(incident,
            slaService.responseStageStatus(incident),
            slaService.resolutionStageStatus(incident));
    }

    private Specification<Incident> and(Specification<Incident> base, Specification<Incident> extra) {
        return base == null ? extra : base.and(extra);
    }

    @Transactional
    public IncidentDetailResponse get(UUID id, AppUserPrincipal principal) {
        Incident incident = require(id);
        assertCanView(incident, principal);
        refreshSla(incident);

        boolean internalVisible = principal.getRole() != RoleName.EMPLOYEE;
        List<CommentResponse> comments = commentRepository.findAllByIncidentIdOrderByCreatedAtAsc(id).stream()
            .filter(c -> internalVisible || !c.isInternal())
            .map(CommentResponse::from)
            .toList();
        List<ActivityResponse> activity = activityRepository.findAllByIncidentIdOrderByCreatedAtAsc(id).stream()
            .map(ActivityResponse::from)
            .toList();

        // An employee does not get the response-stage fields: those are how the
        // support side measures itself, and the DTO simply omits them rather
        // than the UI hiding them.
        return IncidentDetailResponse.from(incident, comments, activity,
            principal.getRole() != RoleName.EMPLOYEE,
            slaService.responseStageStatus(incident),
            slaService.resolutionStageStatus(incident));
    }

    /**
     * The comment thread, oldest first. Internal notes are excluded from an
     * employee's result in the query itself, so they cannot be returned and then
     * filtered away by mistake.
     */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listComments(UUID id, Pageable pageable, AppUserPrincipal principal) {
        Incident incident = require(id);
        assertCanView(incident, principal);

        Page<IncidentComment> page = principal.getRole() == RoleName.EMPLOYEE
            ? commentRepository.findAllByIncidentIdAndInternalFalseOrderByCreatedAtAsc(id, pageable)
            : commentRepository.findAllByIncidentIdOrderByCreatedAtAsc(id, pageable);

        return PageResponse.from(page.map(CommentResponse::from));
    }

    /** The audit trail, newest first. Read-only for every role. */
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> listActivities(UUID id, Pageable pageable, AppUserPrincipal principal) {
        Incident incident = require(id);
        assertCanView(incident, principal);

        return PageResponse.from(
            activityRepository.findAllByIncidentIdOrderByCreatedAtDesc(id, pageable).map(ActivityResponse::from));
    }

    // --------------------------------------------------------------- writes

    @Transactional
    public IncidentDetailResponse create(CreateIncidentRequest request, AppUserPrincipal principal) {
        User reporter = requireUser(principal.getId());

        Incident incident = new Incident();
        incident.setTitle(request.title());
        incident.setDescription(request.description());
        incident.setCategory(request.category());
        incident.setSubcategory(request.subcategory());
        incident.setPriority(request.priority());
        incident.setStatus(IncidentStatus.NEW);
        incident.setReporter(reporter);

        long seq = incidentRepository.findMaxReferenceSeq() + 1;
        incident.setReferenceSeq(seq);
        incident.setReference(String.format("INC-%06d", seq));

        Incident saved = incidentRepository.save(incident);
        // Saved first so createdAt exists: both SLA deadlines are measured from
        // the moment the incident was reported, not from the moment this method
        // happened to run.
        slaService.applyOnCreate(saved, request.priority());

        audit.incidentCreated(saved, reporter);
        notifications.incidentCreated(saved, reporter);
        return get(saved.getId(), principal);
    }

    @Transactional
    public IncidentDetailResponse update(UUID id, UpdateIncidentRequest request, AppUserPrincipal principal) {
        Incident incident = require(id);
        assertCanAct(incident, principal);
        assertNotClosed(incident, "Its details can no longer be changed.");
        User actor = requireUser(principal.getId());

        if (request.title() != null) {
            incident.setTitle(request.title());
        }
        if (request.description() != null) {
            incident.setDescription(request.description());
        }
        if (request.category() != null && !request.category().equals(incident.getCategory())) {
            String previous = incident.getCategory();
            incident.setCategory(request.category());
            audit.fieldUpdated(incident, actor, "Category", previous, request.category());
        }
        if (request.subcategory() != null) {
            incident.setSubcategory(request.subcategory());
        }
        if (request.priority() != null && request.priority() != incident.getPriority()) {
            Priority previous = incident.getPriority();
            incident.setPriority(request.priority());
            // The deadlines follow the new priority; the original policy would
            // otherwise keep governing an incident that is no longer that urgent.
            slaService.recalculateForPriorityChange(incident, request.priority());
            audit.priorityChanged(incident, actor, previous, request.priority());
        }
        refreshSla(incident);
        return get(id, principal);
    }

    @Transactional
    public IncidentDetailResponse assign(UUID id, AssignIncidentRequest request, AppUserPrincipal principal) {
        Incident incident = require(id);
        assertCanAct(incident, principal);
        assertNotClosed(incident, "It can no longer be reassigned.");
        assertMayDirectWork(request, principal);
        User actor = requireUser(principal.getId());

        if (request.agentId() != null) {
            User agent = requireUser(request.agentId());
            if (agent.getRole() == RoleName.EMPLOYEE) {
                throw new BadRequestException("Incidents can only be assigned to an agent or an admin.");
            }
            // A deactivated agent keeps their history but takes no new work.
            if (!agent.isActive()) {
                throw new BadRequestException("That agent is deactivated and cannot take new assignments.");
            }
            User previousAgent = incident.getAssignedAgent();
            incident.setAssignedAgent(agent);
            if (agent.getTeam() != null && request.teamId() == null) {
                incident.setAssignedTeam(agent.getTeam());
            }
            // Putting a named agent on an incident is the first response.
            slaService.markResponded(incident);
            audit.agentAssigned(incident, actor, previousAgent, agent);
            notifications.incidentAssigned(incident, actor, agent);
        }

        if (request.teamId() != null) {
            Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> new NotFoundException("Team not found"));
            Team previousTeam = incident.getAssignedTeam();
            if (previousTeam == null || !previousTeam.getId().equals(team.getId())) {
                incident.setAssignedTeam(team);
                audit.teamAssigned(incident, actor, previousTeam, team);
            }
        }

        if (incident.getStatus() == IncidentStatus.NEW && incident.getAssignedAgent() != null) {
            incident.setStatus(IncidentStatus.ASSIGNED);
            audit.statusChangedAutomatically(incident, actor, IncidentStatus.NEW, IncidentStatus.ASSIGNED);
        }
        refreshSla(incident);
        return get(id, principal);
    }

    @Transactional
    public IncidentDetailResponse changeStatus(UUID id, ChangeStatusRequest request, AppUserPrincipal principal) {
        Incident incident = require(id);
        assertCanAct(incident, principal);
        User actor = requireUser(principal.getId());

        IncidentStatus from = incident.getStatus();
        IncidentStatus to = request.status();
        if (from == to) {
            throw new BadRequestException("Incident is already " + to.name() + ".");
        }
        if (to == IncidentStatus.RESOLVED) {
            throw new BadRequestException("Use the resolve endpoint, which requires a resolution summary.");
        }
        if (to == IncidentStatus.CLOSED) {
            throw new BadRequestException("Use the close endpoint.");
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BadRequestException("Cannot move an incident from " + from.name() + " to " + to.name() + ".");
        }

        incident.setStatus(to);

        if (from == IncidentStatus.RESOLVED) {
            // A reopened incident is no longer resolved; clearing the timestamp
            // keeps resolvedAt meaning "when it was actually resolved".
            incident.setResolvedAt(null);
            incident.setResolutionSummary(null);
            slaService.clearResolution(incident);
            audit.incidentReopened(incident, actor, request.note());
        } else {
            audit.statusChanged(incident, actor, from, to, request.note());
        }
        refreshSla(incident);
        return get(id, principal);
    }

    /**
     * Resolve. Separate from the generic status endpoint because a resolution
     * without a summary is not a resolution - the next person reading the
     * incident has to know what was done.
     */
    @Transactional
    public IncidentDetailResponse resolve(UUID id, ResolveIncidentRequest request, AppUserPrincipal principal) {
        Incident incident = require(id);
        assertCanAct(incident, principal);
        User actor = requireUser(principal.getId());

        IncidentStatus from = incident.getStatus();
        if (from == IncidentStatus.RESOLVED || from == IncidentStatus.CLOSED) {
            throw new BadRequestException("Incident is already " + from.name() + ".");
        }

        Instant resolvedAt = Instant.now();
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolutionSummary(request.resolutionSummary());
        incident.setResolvedAt(resolvedAt);
        slaService.markResolved(incident, resolvedAt);

        audit.incidentResolved(incident, actor, from, request.resolutionSummary());
        notifications.incidentResolved(incident, actor);
        refreshSla(incident);
        return get(id, principal);
    }

    /**
     * Close. Only a resolved incident can be closed, so closure always follows a
     * recorded resolution rather than quietly ending an open incident.
     */
    @Transactional
    public IncidentDetailResponse close(UUID id, CloseIncidentRequest request, AppUserPrincipal principal) {
        Incident incident = require(id);
        assertCanAct(incident, principal);
        User actor = requireUser(principal.getId());

        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new BadRequestException("Incident is already CLOSED.");
        }
        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new BadRequestException("Only a RESOLVED incident can be closed.");
        }

        incident.setStatus(IncidentStatus.CLOSED);
        incident.setClosedAt(Instant.now());

        audit.incidentClosed(incident, actor, request != null ? request.note() : null);
        notifications.incidentClosed(incident, actor);
        refreshSla(incident);
        return get(id, principal);
    }

    @Transactional
    public CommentResponse addComment(UUID id, CreateCommentRequest request, AppUserPrincipal principal) {
        Incident incident = require(id);
        assertCanView(incident, principal);
        assertNotClosed(incident, "Comments can no longer be added.");
        User author = requireUser(principal.getId());

        IncidentComment comment = new IncidentComment();
        comment.setIncident(incident);
        comment.setAuthor(author);
        comment.setComment(request.comment().trim());
        // An employee cannot post an internal note, whatever the request says.
        comment.setInternal(request.internal() && principal.getRole() != RoleName.EMPLOYEE);
        IncidentComment saved = commentRepository.save(comment);

        audit.commentAdded(incident, author, saved.isInternal());
        notifications.commentAdded(incident, author, saved.isInternal());

        // An agent replying is a first response even if nobody was assigned yet.
        // A comment from the reporter is not.
        if (principal.getRole() != RoleName.EMPLOYEE) {
            slaService.markResponded(incident);
        }
        refreshSla(incident);

        return CommentResponse.from(saved);
    }

    // ------------------------------------------------------- authorization

    /** Who may see an incident at all. */
    private void assertCanView(Incident incident, AppUserPrincipal principal) {
        switch (principal.getRole()) {
            case ADMIN -> {
                return;
            }
            case EMPLOYEE -> {
                if (!incident.getReporter().getId().equals(principal.getId())) {
                    throw new AccessDeniedException("You can only view incidents you reported.");
                }
            }
            case SUPPORT_AGENT -> {
                if (!isOnAgentQueue(incident, principal)) {
                    throw new AccessDeniedException("This incident is not assigned to you or your team.");
                }
            }
        }
    }

    /** Who may change an incident. An employee never can, not even their own. */
    private void assertCanAct(Incident incident, AppUserPrincipal principal) {
        if (principal.getRole() == RoleName.EMPLOYEE) {
            throw new AccessDeniedException("Employees cannot modify incidents. Add a comment instead.");
        }
        assertCanView(incident, principal);
    }

    /**
     * Who may point work at someone other than themselves.
     *
     * <p>An agent takes an incident or leaves it: the queue is a pool, and
     * whoever claims a thing owns it. Handing work to a colleague, or moving it
     * between teams, is a scheduling decision and belongs to an administrator.
     *
     * <p>Enforced here rather than by hiding the controls, because the screen
     * is not what stops a request - this is.
     */
    private void assertMayDirectWork(AssignIncidentRequest request, AppUserPrincipal principal) {
        if (principal.getRole() != RoleName.SUPPORT_AGENT) {
            return;
        }
        if (request.agentId() != null && !request.agentId().equals(principal.getId())) {
            throw new AccessDeniedException(
                "You can take an incident yourself, but only an administrator can assign it to someone else.");
        }
        if (request.teamId() != null) {
            throw new AccessDeniedException("Only an administrator can move an incident between teams.");
        }
    }

    /**
     * A closed incident is final in V1. {@code changeStatus}, {@code resolve} and
     * {@code close} each refuse it on their own terms; this is the same rule for
     * the writes that carry no status check of their own, so the screen hiding a
     * control is not the only thing stopping the request.
     */
    private void assertNotClosed(Incident incident, String consequence) {
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new BadRequestException("This incident is closed. " + consequence);
        }
    }

    private boolean isOnAgentQueue(Incident incident, AppUserPrincipal principal) {
        boolean mine = incident.getAssignedAgent() != null
            && incident.getAssignedAgent().getId().equals(principal.getId());
        boolean myTeam = incident.getAssignedTeam() != null && principal.getTeamId() != null
            && incident.getAssignedTeam().getId().equals(principal.getTeamId());
        boolean untriaged = incident.getAssignedTeam() == null && incident.getStatus() == IncidentStatus.NEW;
        return mine || myTeam || untriaged;
    }

    // -------------------------------------------------------------- helpers

    private Incident require(UUID id) {
        return incidentRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Incident not found"));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    /**
     * Recomputes the SLA status and records whatever the change deserves.
     *
     * <p>This is the same call the background sweep makes. Reading an incident
     * and sweeping it must reach the same verdict, so both go through
     * {@link SlaService#refreshAndRecord} rather than each judging for itself -
     * and whichever gets there first is the one that records the breach.
     */
    private void refreshSla(Incident incident) {
        slaService.refreshAndRecord(incident);
    }
}
