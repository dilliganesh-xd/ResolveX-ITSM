package com.rewin.itsm.web;

import com.rewin.itsm.common.PageResponse;
import com.rewin.itsm.dto.admin.AdminAuditResponse;
import com.rewin.itsm.dto.admin.CreateTeamRequest;
import com.rewin.itsm.dto.admin.CreateUserRequest;
import com.rewin.itsm.dto.admin.ReportSummaryResponse;
import com.rewin.itsm.dto.admin.ResetPasswordRequest;
import com.rewin.itsm.dto.admin.SlaPolicyRequest;
import com.rewin.itsm.dto.admin.SlaPolicyResponse;
import com.rewin.itsm.dto.admin.TeamMemberResponse;
import com.rewin.itsm.dto.admin.TeamResponse;
import com.rewin.itsm.dto.admin.UpdateTeamRequest;
import com.rewin.itsm.dto.admin.UpdateUserRequest;
import com.rewin.itsm.dto.admin.UserDetailResponse;
import com.rewin.itsm.dto.admin.UserFilter;
import com.rewin.itsm.dto.auth.UserResponse;
import com.rewin.itsm.security.AppUserPrincipal;
import com.rewin.itsm.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User, team, SLA policy, report and admin-audit endpoints.
 *
 * <p>Reads of teams and SLA policies are open to any authenticated user, because
 * the agent queue needs team names and the incident view shows the policy that
 * applies. Every write, and everything under /api/users, /api/reports and
 * /api/admin, is admin-only - enforced both here and by path in SecurityConfig,
 * so removing one annotation by accident does not open a door.
 *
 * <p>There is no delete endpoint for a user, a team or a policy. Each is
 * referenced by incidents or history, so administration deactivates rather than
 * removes.
 */
@RestController
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ----------------------------------------------------------------- users

    @GetMapping("/api/users")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> listUsers(
            UserFilter filter,
            @PageableDefault(size = 50) Pageable pageable) {
        return adminService.listUsers(filter, pageable);
    }

    @GetMapping("/api/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse getUser(@PathVariable UUID id) {
        return adminService.getUser(id);
    }

    @PostMapping("/api/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createUser(request, actor));
    }

    @PutMapping("/api/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUser(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateUserRequest request,
                                   @AuthenticationPrincipal AppUserPrincipal actor) {
        return adminService.updateUser(id, request, actor);
    }

    @PatchMapping("/api/users/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse setUserActive(@PathVariable UUID id,
                                      @RequestParam boolean active,
                                      @AuthenticationPrincipal AppUserPrincipal actor) {
        return adminService.setUserActive(id, active, actor);
    }

    /**
     * Admin-assisted password reset. Separate from the profile edit so a
     * credential is never changed as a side effect of editing a name.
     */
    @PostMapping("/api/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse resetPassword(@PathVariable UUID id,
                                      @Valid @RequestBody ResetPasswordRequest request,
                                      @AuthenticationPrincipal AppUserPrincipal actor) {
        return adminService.resetPassword(id, request.password(), actor);
    }

    /**
     * The assignable agents, for the assignment control. Separate from
     * /api/users because that is admin-only and exposes every account.
     */
    @GetMapping("/api/agents")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','ADMIN')")
    public List<UserResponse> listAgents() {
        return adminService.listAgents();
    }

    // ----------------------------------------------------------------- teams

    @GetMapping("/api/teams")
    public List<TeamResponse> listTeams() {
        return adminService.listTeams();
    }

    @GetMapping("/api/teams/{id}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TeamMemberResponse> listTeamMembers(@PathVariable UUID id) {
        return adminService.listTeamMembers(id);
    }

    @PostMapping("/api/teams")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamResponse> createTeam(
            @Valid @RequestBody CreateTeamRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTeam(request, actor));
    }

    @PutMapping("/api/teams/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TeamResponse updateTeam(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateTeamRequest request,
                                   @AuthenticationPrincipal AppUserPrincipal actor) {
        return adminService.updateTeam(id, request, actor);
    }

    @PatchMapping("/api/teams/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public TeamResponse setTeamActive(@PathVariable UUID id,
                                      @RequestParam boolean active,
                                      @AuthenticationPrincipal AppUserPrincipal actor) {
        return adminService.setTeamActive(id, active, actor);
    }

    // ---------------------------------------------------------- sla policies

    @GetMapping("/api/sla-policies")
    public List<SlaPolicyResponse> listSlaPolicies() {
        return adminService.listSlaPolicies();
    }

    @PostMapping("/api/sla-policies")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SlaPolicyResponse> createSlaPolicy(
            @Valid @RequestBody SlaPolicyRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createSlaPolicy(request, actor));
    }

    @PutMapping("/api/sla-policies/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SlaPolicyResponse updateSlaPolicy(@PathVariable UUID id,
                                             @Valid @RequestBody SlaPolicyRequest request,
                                             @AuthenticationPrincipal AppUserPrincipal actor) {
        return adminService.updateSlaPolicy(id, request, actor);
    }

    @PatchMapping("/api/sla-policies/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public SlaPolicyResponse setSlaPolicyActive(@PathVariable UUID id,
                                                @RequestParam boolean active,
                                                @AuthenticationPrincipal AppUserPrincipal actor) {
        return adminService.setSlaPolicyActive(id, active, actor);
    }

    // ------------------------------------------------- reports and audit

    @GetMapping("/api/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ReportSummaryResponse report() {
        return adminService.report();
    }

    /** What administrators have done. Read-only; nothing writes through the API. */
    @GetMapping("/api/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminAuditResponse> adminAudit(@PageableDefault(size = 100) Pageable pageable) {
        return adminService.adminAudit(pageable);
    }
}
