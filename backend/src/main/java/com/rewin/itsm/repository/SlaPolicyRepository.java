package com.rewin.itsm.repository;

import com.rewin.itsm.domain.enums.Priority;
import com.rewin.itsm.domain.sla.SlaPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {

    /** The policy applied to a new incident of this priority. */
    Optional<SlaPolicy> findFirstByPriorityAndActiveTrue(Priority priority);

    List<SlaPolicy> findAllByOrderByPriorityAsc();

    /** Used to stand down the other policies when one is activated. */
    List<SlaPolicy> findAllByPriorityAndActiveTrue(Priority priority);
}
