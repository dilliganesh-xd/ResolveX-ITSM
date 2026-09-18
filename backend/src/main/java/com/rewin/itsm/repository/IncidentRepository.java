package com.rewin.itsm.repository;

import com.rewin.itsm.domain.enums.IncidentStatus;
import com.rewin.itsm.domain.incident.Incident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByReference(String reference);

    long countByStatus(IncidentStatus status);

    /** Workload figures for the admin user detail view. */
    long countByAssignedAgentId(UUID agentId);

    long countByAssignedAgentIdAndStatusIn(UUID agentId, java.util.Collection<IncidentStatus> statuses);

    long countByAssignedTeamId(UUID teamId);

    @Query("select coalesce(max(i.referenceSeq), 0) from Incident i")
    long findMaxReferenceSeq();

    /**
     * What the SLA sweep looks at: everything not yet closed that actually has a
     * deadline. A closed incident is finished, and an incident with no active
     * policy has no SLA to judge, so neither is worth loading every minute.
     *
     * <p>The policy is fetched with the incident because the at-risk threshold
     * reads its configured window; without this the sweep would fire one query
     * per row.
     */
    @Query("""
        select distinct i from Incident i
        left join fetch i.slaPolicy
        where i.status <> :closed
          and (i.responseDeadline is not null or i.resolutionDeadline is not null)
        """)
    List<Incident> findSweepCandidates(IncidentStatus closed);
}
