package com.rewin.itsm.repository;

import com.rewin.itsm.domain.incident.IncidentComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, UUID> {

    /** Chronological: a conversation reads oldest first. */
    List<IncidentComment> findAllByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    Page<IncidentComment> findAllByIncidentIdOrderByCreatedAtAsc(UUID incidentId, Pageable pageable);

    /** An employee never sees internal notes, so they are excluded in the query. */
    Page<IncidentComment> findAllByIncidentIdAndInternalFalseOrderByCreatedAtAsc(UUID incidentId, Pageable pageable);
}
