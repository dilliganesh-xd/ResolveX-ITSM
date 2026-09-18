package com.rewin.itsm.service;

import com.rewin.itsm.domain.audit.AdminAuditEntry;
import com.rewin.itsm.domain.user.User;
import com.rewin.itsm.repository.AdminAuditRepository;
import org.springframework.stereotype.Service;

/**
 * Records administrative actions. Append-only, with descriptions built from the
 * real actor and target rather than assembled anywhere else.
 */
@Service
public class AdminAuditService {

    public static final String TARGET_USER = "USER";
    public static final String TARGET_TEAM = "TEAM";
    public static final String TARGET_SLA_POLICY = "SLA_POLICY";

    private final AdminAuditRepository repository;

    public AdminAuditService(AdminAuditRepository repository) {
        this.repository = repository;
    }

    public void userCreated(User actor, User created) {
        record(actor, TARGET_USER, created.getName(),
            "User " + created.getName() + " created as " + created.getRole().name() + " by " + nameOf(actor));
    }

    public void userUpdated(User actor, User updated, String changes) {
        record(actor, TARGET_USER, updated.getName(),
            "User " + updated.getName() + " updated by " + nameOf(actor) + ": " + changes);
    }

    public void userActiveChanged(User actor, User target, boolean active) {
        record(actor, TARGET_USER, target.getName(),
            "User " + target.getName() + (active ? " activated by " : " deactivated by ") + nameOf(actor));
    }

    /** Records that a reset happened. Never what the password was set to. */
    public void userPasswordReset(User actor, User target) {
        record(actor, TARGET_USER, target.getName(),
            "Password reset for " + target.getName() + " by " + nameOf(actor));
    }

    public void teamCreated(User actor, String name) {
        record(actor, TARGET_TEAM, name, "Team " + name + " created by " + nameOf(actor));
    }

    public void teamUpdated(User actor, String name, String changes) {
        record(actor, TARGET_TEAM, name, "Team " + name + " updated by " + nameOf(actor) + ": " + changes);
    }

    public void teamActiveChanged(User actor, String name, boolean active) {
        record(actor, TARGET_TEAM, name,
            "Team " + name + (active ? " activated by " : " deactivated by ") + nameOf(actor));
    }

    public void policyCreated(User actor, String name) {
        record(actor, TARGET_SLA_POLICY, name, "SLA policy " + name + " created by " + nameOf(actor));
    }

    public void policyUpdated(User actor, String name) {
        record(actor, TARGET_SLA_POLICY, name, "SLA policy " + name + " updated by " + nameOf(actor));
    }

    public void policyActiveChanged(User actor, String name, boolean active) {
        record(actor, TARGET_SLA_POLICY, name,
            "SLA policy " + name + (active ? " activated by " : " deactivated by ") + nameOf(actor));
    }

    /** Recorded when activating one policy stands another down, so the side effect is not silent. */
    public void policyStoodDown(User actor, String name, String activatedName) {
        record(actor, TARGET_SLA_POLICY, name,
            "SLA policy " + name + " deactivated automatically because " + activatedName
                + " was activated for the same priority");
    }

    private void record(User actor, String targetType, String targetName, String description) {
        repository.save(new AdminAuditEntry(actor, targetType, truncate(targetName, 160),
            truncate(description, 500)));
    }

    private String nameOf(User user) {
        return user != null ? user.getName() : "System";
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
