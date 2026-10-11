package com.rewin.itsm.service;

import com.rewin.itsm.common.BadRequestException;
import com.rewin.itsm.common.ConflictException;
import com.rewin.itsm.common.NotFoundException;
import com.rewin.itsm.common.PageResponse;
import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.enums.SlaStatus;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.sla.SlaPolicy;
import com.rewin.itsm.domain.team.Team;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.dto.admin.AdminAuditResponse;
import com.rewin.itsm.dto.admin.CreateTeamRequest;
import com.rewin.itsm.dto.admin.CreateUserRequest;
import com.rewin.itsm.dto.admin.ReportSummaryResponse;
import com.rewin.itsm.dto.admin.SlaPolicyRequest;
import com.rewin.itsm.dto.admin.SlaPolicyResponse;
import com.rewin.itsm.dto.admin.TeamMemberResponse;
import com.rewin.itsm.dto.admin.TeamResponse;
import com.rewin.itsm.dto.admin.UpdateTeamRequest;
import com.rewin.itsm.dto.admin.UpdateUserRequest;
import com.rewin.itsm.dto.admin.UserDetailResponse;
import com.rewin.itsm.dto.admin.UserFilter;
import com.rewin.itsm.dto.admin.WorkloadRow;
import com.rewin.itsm.dto.auth.UserResponse;
import com.rewin.itsm.repository.AdminAuditRepository;
import com.rewin.itsm.repository.IncidentRepository;
import com.rewin.itsm.repository.SlaPolicyRepository;
import com.rewin.itsm.repository.TeamRepository;
import com.rewin.itsm.repository.UserRepository;
import com.rewin.itsm.repository.UserSpecifications;
import com.rewin.itsm.security.AppUserPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User, team, SLA policy, report and admin-audit operations.
 *
 * <p>All of these are admin-only, gated by path in SecurityConfig and again by
 * {@code @PreAuthorize} on the controller. They are one service because every
 * method here is a thin CRUD wrapper; splitting them would add files, not
 * clarity.
 *
 * <p>Nothing here touches SLA calculation. Editing a policy changes which policy
 * the <em>next</em> incident gets; incidents already created keep the deadlines
 * they were stamped with, which is what makes a past breach a fact rather than a
 * function of the current configuration.
 */
@Service
public class AdminService {

    /** The cache holding {@link #listSlaPolicies()}; see CacheConfig. */
    public static final String SLA_POLICIES_CACHE = "slaPolicies";

    /** Statuses that count as an incident an agent has finished. */
    private static final Set<IncidentStatus> FINISHED =
        Set.of(IncidentStatus.RESOLVED, IncidentStatus.CLOSED);

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final IncidentRepository incidentRepository;
    private final AdminAuditRepository adminAuditRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService audit;

    public AdminService(UserRepository userRepository, TeamRepository teamRepository,
                        SlaPolicyRepository slaPolicyRepository, IncidentRepository incidentRepository,
                        AdminAuditRepository adminAuditRepository, PasswordEncoder passwordEncoder,
                        AdminAuditService audit) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.incidentRepository = incidentRepository;
        this.adminAuditRepository = adminAuditRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    // ----------------------------------------------------------------- users

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(UserFilter filter, Pageable pageable) {
        Specification<User> spec = null;
        if (filter != null) {
            if (filter.hasSearch()) {
                spec = and(spec, UserSpecifications.matches(filter.search().trim()));
            }
            if (filter.role() != null) {
                spec = and(spec, UserSpecifications.hasRole(filter.role()));
            }
            if (filter.teamId() != null) {
                spec = and(spec, UserSpecifications.hasTeam(filter.teamId()));
            }
            if (filter.active() != null) {
                spec = and(spec, UserSpecifications.isActive(filter.active()));
            }
        }

        Pageable sorted = pageable.getSort().isSorted()
            ? pageable
            : org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name"));

        Page<User> page = userRepository.findAll(spec, sorted);
        return PageResponse.from(page.map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUser(UUID id) {
        User user = requireUser(id);
        return UserDetailResponse.from(user,
            incidentRepository.countByAssignedAgentId(id),
            incidentRepository.countByAssignedAgentIdAndStatusIn(id, FINISHED));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, AppUserPrincipal actor) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("A user with that email already exists.");
        }
        User user = new User(
            request.email().trim().toLowerCase(),
            passwordEncoder.encode(request.password()),
            request.name().trim(),
            request.role());

        if (request.teamId() != null) {
            user.setTeam(requireTeam(request.teamId()));
        }
        User saved = userRepository.save(user);
        audit.userCreated(actorOf(actor), saved);
        return UserResponse.from(saved);
    }

    /**
     * Edits a profile. The password is untouched: this request has no password
     * field, so an edit cannot reset a credential as a side effect.
     */
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request, AppUserPrincipal actor) {
        User user = requireUser(id);

        String email = request.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A user with that email already exists.");
        }

        // An admin who removes their own admin role, or deactivates themselves,
        // locks themselves out of the only screen that could undo it.
        if (actor != null && user.getId().equals(actor.getId())) {
            if (request.role() != RoleName.ADMIN) {
                throw new BadRequestException("You cannot change your own role.");
            }
            if (!request.active()) {
                throw new BadRequestException("You cannot deactivate your own account.");
            }
        }

        List<String> changes = new ArrayList<>();
        if (!user.getName().equals(request.name().trim())) {
            changes.add("name " + user.getName() + " to " + request.name().trim());
            user.setName(request.name().trim());
        }
        if (!user.getEmail().equalsIgnoreCase(email)) {
            changes.add("email " + user.getEmail() + " to " + email);
            user.setEmail(email);
        }
        if (user.getRole() != request.role()) {
            changes.add("role " + user.getRole().name() + " to " + request.role().name());
            user.setRole(request.role());
        }
        if (user.isActive() != request.active()) {
            changes.add(request.active() ? "activated" : "deactivated");
            user.setActive(request.active());
        }

        if (request.clearTeam()) {
            if (user.getTeam() != null) {
                changes.add("removed from " + user.getTeam().getName());
                user.setTeam(null);
            }
        } else if (request.teamId() != null) {
            Team team = requireTeam(request.teamId());
            if (user.getTeam() == null || !user.getTeam().getId().equals(team.getId())) {
                changes.add("team to " + team.getName());
                user.setTeam(team);
            }
        }

        if (!changes.isEmpty()) {
            audit.userUpdated(actorOf(actor), user, String.join(", ", changes));
        }
        return UserResponse.from(user);
    }

    /**
     * Deactivation, not deletion. Incidents and activity keep pointing at the
     * user, so history stays readable; the account simply stops working.
     */
    @Transactional
    public UserResponse setUserActive(UUID id, boolean active, AppUserPrincipal actor) {
        User user = requireUser(id);
        if (!active && actor != null && user.getId().equals(actor.getId())) {
            throw new BadRequestException("You cannot deactivate your own account.");
        }
        if (user.isActive() != active) {
            user.setActive(active);
            audit.userActiveChanged(actorOf(actor), user, active);
        }
        return UserResponse.from(user);
    }

    /**
     * Admin-assisted password reset.
     *
     * <p>The new password is hashed before it touches the database and appears in
     * no response and no log line - the audit entry records that a reset
     * happened, never what it was set to.
     */
    @Transactional
    public UserResponse resetPassword(UUID id, String rawPassword, AppUserPrincipal actor) {
        User user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        audit.userPasswordReset(actorOf(actor), user);
        return UserResponse.from(user);
    }

    /** Assignable agents. Inactive ones are excluded: they cannot take new work. */
    @Transactional(readOnly = true)
    public List<UserResponse> listAgents() {
        return userRepository.findAllByRoleAndActiveTrueOrderByNameAsc(RoleName.SUPPORT_AGENT).stream()
            .map(UserResponse::from)
            .toList();
    }

    // ----------------------------------------------------------------- teams

    @Transactional(readOnly = true)
    public List<TeamResponse> listTeams() {
        return teamRepository.findAll().stream()
            .sorted(Comparator.comparing(Team::getName))
            .map(team -> TeamResponse.from(team, userRepository.countByTeamId(team.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listTeamMembers(UUID teamId) {
        requireTeam(teamId);
        return userRepository.findAllByTeamIdOrderByNameAsc(teamId).stream()
            .map(TeamMemberResponse::from)
            .toList();
    }

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, AppUserPrincipal actor) {
        if (teamRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A team with that name already exists.");
        }
        Team saved = teamRepository.save(new Team(request.name().trim(), request.description()));
        audit.teamCreated(actorOf(actor), saved.getName());
        return TeamResponse.from(saved, 0);
    }

    @Transactional
    public TeamResponse updateTeam(UUID id, UpdateTeamRequest request, AppUserPrincipal actor) {
        Team team = requireTeam(id);
        String name = request.name().trim();

        if (!team.getName().equalsIgnoreCase(name) && teamRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A team with that name already exists.");
        }

        List<String> changes = new ArrayList<>();
        if (!team.getName().equals(name)) {
            changes.add("name " + team.getName() + " to " + name);
            team.setName(name);
        }
        if (request.description() != null && !request.description().equals(team.getDescription())) {
            changes.add("description updated");
            team.setDescription(request.description());
        }
        if (team.isActive() != request.active()) {
            changes.add(request.active() ? "activated" : "deactivated");
            team.setActive(request.active());
        }

        if (!changes.isEmpty()) {
            audit.teamUpdated(actorOf(actor), team.getName(), String.join(", ", changes));
        }
        return TeamResponse.from(team, userRepository.countByTeamId(team.getId()));
    }

    /**
     * Deactivation, again not deletion: incidents routed to a team and users
     * belonging to it must keep resolving, so the row has to stay.
     */
    @Transactional
    public TeamResponse setTeamActive(UUID id, boolean active, AppUserPrincipal actor) {
        Team team = requireTeam(id);
        if (team.isActive() != active) {
            team.setActive(active);
            audit.teamActiveChanged(actorOf(actor), team.getName(), active);
        }
        return TeamResponse.from(team, userRepository.countByTeamId(team.getId()));
    }

    // ---------------------------------------------------------- sla policies

    /**
     * Cached: every role reads the same list, and it changes only through the
     * three writes below, each of which empties the cache.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SLA_POLICIES_CACHE)
    public List<SlaPolicyResponse> listSlaPolicies() {
        // Sorted in Java, by severity. The priority column stores the enum name,
        // so ordering it in SQL would sort alphabetically - CRITICAL, HIGH, LOW,
        // MEDIUM - which reads as nonsense on the policy screen.
        return slaPolicyRepository.findAll().stream()
            .sorted(Comparator.comparing((SlaPolicy policy) -> policy.getPriority().ordinal()).reversed()
                .thenComparing(SlaPolicy::getName))
            .map(SlaPolicyResponse::from)
            .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = SLA_POLICIES_CACHE, allEntries = true)
    public SlaPolicyResponse createSlaPolicy(SlaPolicyRequest request, AppUserPrincipal actor) {
        validatePolicy(request);
        SlaPolicy policy = new SlaPolicy(request.name().trim(), request.priority(),
            request.responseTimeMinutes(), request.resolutionTimeMinutes());
        policy.setActive(request.active());
        SlaPolicy saved = slaPolicyRepository.save(policy);
        audit.policyCreated(actorOf(actor), saved.getName());
        if (saved.isActive()) {
            standDownOthers(saved, actorOf(actor));
        }
        return SlaPolicyResponse.from(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = SLA_POLICIES_CACHE, allEntries = true)
    public SlaPolicyResponse updateSlaPolicy(UUID id, SlaPolicyRequest request, AppUserPrincipal actor) {
        validatePolicy(request);
        SlaPolicy policy = slaPolicyRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("SLA policy not found"));
        policy.setName(request.name().trim());
        policy.setPriority(request.priority());
        policy.setResponseTimeMinutes(request.responseTimeMinutes());
        policy.setResolutionTimeMinutes(request.resolutionTimeMinutes());
        policy.setActive(request.active());
        audit.policyUpdated(actorOf(actor), policy.getName());
        if (policy.isActive()) {
            standDownOthers(policy, actorOf(actor));
        }
        return SlaPolicyResponse.from(policy);
    }

    /**
     * Activate or deactivate a policy.
     *
     * <p>Incidents already created keep the deadlines they were stamped with;
     * this only changes which policy the next incident of that priority gets.
     */
    @Transactional
    @CacheEvict(cacheNames = SLA_POLICIES_CACHE, allEntries = true)
    public SlaPolicyResponse setSlaPolicyActive(UUID id, boolean active, AppUserPrincipal actor) {
        SlaPolicy policy = slaPolicyRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("SLA policy not found"));
        if (policy.isActive() != active) {
            policy.setActive(active);
            audit.policyActiveChanged(actorOf(actor), policy.getName(), active);
        }
        if (active) {
            standDownOthers(policy, actorOf(actor));
        }
        return SlaPolicyResponse.from(policy);
    }

    private void validatePolicy(SlaPolicyRequest request) {
        if (request.responseTimeMinutes() <= 0 || request.resolutionTimeMinutes() <= 0) {
            throw new BadRequestException("Response and resolution times must be positive.");
        }
        if (request.responseTimeMinutes() > request.resolutionTimeMinutes()) {
            throw new BadRequestException("Resolution time cannot be shorter than response time.");
        }
    }

    /**
     * Exactly one policy is active per priority, and activating one stands the
     * rest down rather than refusing.
     *
     * <p>Refusing would leave an admin unable to swap a policy without first
     * remembering to disable the old one - and a priority briefly left with no
     * active policy creates incidents carrying no SLA at all, which is the worse
     * failure. Standing the old one down is atomic and cannot leave that gap.
     * Each stand-down is written to the admin audit, so the side effect is
     * visible rather than silent.
     */
    private void standDownOthers(SlaPolicy activated, User actor) {
        slaPolicyRepository.findAllByPriorityAndActiveTrue(activated.getPriority()).stream()
            .filter(other -> !other.getId().equals(activated.getId()))
            .forEach(other -> {
                other.setActive(false);
                audit.policyStoodDown(actor, other.getName(), activated.getName());
            });
    }

    // --------------------------------------------------------------- reports

    /**
     * V1 reports are counts over the whole incident table. This is a full scan
     * and is fine at MVP volume; it becomes a set of grouped queries once the
     * table is large enough to notice.
     */
    @Transactional(readOnly = true)
    public ReportSummaryResponse report() {
        List<Incident> all = incidentRepository.findAll();

        Map<IncidentStatus, Long> byStatus = new EnumMap<>(IncidentStatus.class);
        for (IncidentStatus status : IncidentStatus.values()) {
            byStatus.put(status, all.stream().filter(i -> i.getStatus() == status).count());
        }
        Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
        for (Priority priority : Priority.values()) {
            byPriority.put(priority, all.stream().filter(i -> i.getPriority() == priority).count());
        }

        // Unrouted incidents are counted too: "nobody owns this" is exactly the
        // figure an admin needs, and leaving it out would hide it.
        Map<String, Long> byTeam = new LinkedHashMap<>();
        teamRepository.findAll().stream()
            .sorted(Comparator.comparing(Team::getName))
            .forEach(team -> byTeam.put(team.getName(), incidentRepository.countByAssignedTeamId(team.getId())));
        byTeam.put("Unrouted", all.stream().filter(i -> i.getAssignedTeam() == null).count());

        long open = all.stream()
            .filter(i -> i.getStatus() != IncidentStatus.RESOLVED && i.getStatus() != IncidentStatus.CLOSED)
            .count();
        long atRisk = all.stream().filter(i -> i.getSlaStatus() == SlaStatus.AT_RISK).count();
        long breached = all.stream().filter(i -> i.getSlaStatus() == SlaStatus.BREACHED).count();

        long onTrack = all.stream().filter(i -> i.getSlaStatus() == SlaStatus.ON_TRACK).count();

        // Workload rows: one per team, one per agent, each counting the states
        // that matter for "who is carrying what right now".
        List<WorkloadRow> teamWorkload = teamRepository.findAll().stream()
            .sorted(Comparator.comparing(Team::getName))
            .map(team -> workload(team.getName(),
                all.stream().filter(i -> i.getAssignedTeam() != null
                    && i.getAssignedTeam().getId().equals(team.getId())).toList()))
            .toList();

        List<WorkloadRow> agentWorkload = userRepository.findAllByRole(RoleName.SUPPORT_AGENT).stream()
            .sorted(Comparator.comparing(User::getName))
            .map(agent -> workload(agent.getName(),
                all.stream().filter(i -> i.getAssignedAgent() != null
                    && i.getAssignedAgent().getId().equals(agent.getId())).toList()))
            .toList();

        return new ReportSummaryResponse(all.size(), byStatus, byPriority, byTeam, open, onTrack, atRisk,
            breached,
            byStatus.getOrDefault(IncidentStatus.RESOLVED, 0L),
            byStatus.getOrDefault(IncidentStatus.CLOSED, 0L),
            byStatus.getOrDefault(IncidentStatus.IN_PROGRESS, 0L),
            userRepository.count(), userRepository.countByActiveTrue(), teamRepository.count(),
            teamWorkload, agentWorkload);
    }

    private WorkloadRow workload(String name, List<Incident> incidents) {
        long open = incidents.stream()
            .filter(i -> i.getStatus() == IncidentStatus.NEW || i.getStatus() == IncidentStatus.ASSIGNED)
            .count();
        long inProgress = incidents.stream()
            .filter(i -> i.getStatus() == IncidentStatus.IN_PROGRESS).count();
        long resolved = incidents.stream()
            .filter(i -> i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED)
            .count();
        return new WorkloadRow(name, open, inProgress, resolved, incidents.size());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditResponse> adminAudit(Pageable pageable) {
        return PageResponse.from(
            adminAuditRepository.findAllByOrderByCreatedAtDesc(pageable).map(AdminAuditResponse::from));
    }

    // --------------------------------------------------------------- helpers

    private Specification<User> and(Specification<User> base, Specification<User> extra) {
        return base == null ? extra : base.and(extra);
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Team requireTeam(UUID id) {
        return teamRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Team not found"));
    }

    /** The acting admin as an entity, for the audit trail. Null stays null. */
    private User actorOf(AppUserPrincipal principal) {
        return principal == null ? null : userRepository.findById(principal.getId()).orElse(null);
    }
}
