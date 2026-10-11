package com.rewin.itsm.repository;

import com.rewin.itsm.domain.incident.IncidentAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentAttachmentRepository extends JpaRepository<IncidentAttachment, UUID> {

    /** Oldest first, like the comment thread. */
    List<IncidentAttachment> findAllByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    /**
     * Scoped to the incident in the URL, so access to one incident can never be
     * used to fetch an attachment that belongs to another.
     */
    Optional<IncidentAttachment> findByIdAndIncidentId(UUID id, UUID incidentId);
}
