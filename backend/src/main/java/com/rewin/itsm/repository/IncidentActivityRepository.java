package com.rewin.itsm.repository;

import com.rewin.itsm.domain.incident.IncidentActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Append-only. This interface deliberately exposes no delete or update helper;
 * the audit trail is written once and read thereafter.
 */
public interface IncidentActivityRepository extends JpaRepository<IncidentActivity, UUID> {

    /** Ascending, for the timeline embedded in the incident detail. */
    List<IncidentActivity> findAllByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    /** Newest first, which is what the activities endpoint returns. */
    Page<IncidentActivity> findAllByIncidentIdOrderByCreatedAtDesc(UUID incidentId, Pageable pageable);
}
