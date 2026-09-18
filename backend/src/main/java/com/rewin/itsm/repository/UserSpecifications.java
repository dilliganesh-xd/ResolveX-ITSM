package com.rewin.itsm.repository;

import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.team.Team;
import com.rewin.itsm.domain.user.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters for the admin user list, composed into one query rather than applied
 * in memory after loading everyone.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Matches name or email, case-insensitively. */
    public static Specification<User> matches(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("email")), pattern));
        };
    }

    public static Specification<User> hasRole(RoleName role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    /** LEFT join: an implicit join would silently drop users with no team. */
    public static Specification<User> hasTeam(UUID teamId) {
        return (root, query, cb) -> {
            Join<User, Team> team = root.join("team", JoinType.LEFT);
            return cb.equal(team.get("id"), teamId);
        };
    }

    public static Specification<User> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
