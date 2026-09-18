package com.rewin.itsm.repository;

import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.enums.SlaStatus;
import com.rewin.itsm.domain.incident.Incident;
import com.rewin.itsm.domain.team.Team;
import com.rewin.itsm.domain.user.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable pieces of the incident query.
 *
 * <p>The role scope and the user's filters are combined into a single query
 * rather than fetched and filtered in memory, so the queue stays correct once
 * paging is added in a later phase.
 *
 * <p>Every relation is reached through an explicit LEFT join. An implicit join
 * would be an inner join, which silently drops unassigned incidents - exactly
 * the rows a triage queue most needs to show.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    /** An employee sees only what they reported. */
    public static Specification<Incident> reportedBy(UUID reporterId) {
        return (root, query, cb) -> {
            Join<Incident, User> reporter = root.join("reporter", JoinType.LEFT);
            return cb.equal(reporter.get("id"), reporterId);
        };
    }

    /**
     * An agent's queue: their team's incidents, plus anything assigned to them
     * personally, plus untriaged incidents nobody has routed yet.
     */
    public static Specification<Incident> agentScope(UUID teamId, UUID agentId) {
        return (root, query, cb) -> {
            Join<Incident, Team> team = root.join("assignedTeam", JoinType.LEFT);
            Join<Incident, User> agent = root.join("assignedAgent", JoinType.LEFT);

            var untriaged = cb.and(
                cb.isNull(team.get("id")),
                cb.equal(root.get("status"), IncidentStatus.NEW));

            if (teamId == null) {
                return cb.or(cb.equal(agent.get("id"), agentId), untriaged);
            }
            return cb.or(
                cb.equal(team.get("id"), teamId),
                cb.equal(agent.get("id"), agentId),
                untriaged);
        };
    }

    public static Specification<Incident> hasStatus(IncidentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Incident> hasPriority(Priority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Incident> hasSlaStatus(SlaStatus slaStatus) {
        return (root, query, cb) -> cb.equal(root.get("slaStatus"), slaStatus);
    }

    public static Specification<Incident> hasTeam(UUID teamId) {
        return (root, query, cb) -> {
            Join<Incident, Team> team = root.join("assignedTeam", JoinType.LEFT);
            return cb.equal(team.get("id"), teamId);
        };
    }

    /** Free-text match over reference, title, description and reporter name. */
    public static Specification<Incident> matches(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            Join<Incident, User> reporter = root.join("reporter", JoinType.LEFT);
            return cb.or(
                cb.like(cb.lower(root.get("reference")), pattern),
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(reporter.get("name")), pattern));
        };
    }
}
