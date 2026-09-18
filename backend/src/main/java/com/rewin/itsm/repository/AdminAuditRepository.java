package com.rewin.itsm.repository;

import com.rewin.itsm.domain.audit.AdminAuditEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only: no update or delete helper is exposed. */
public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, UUID> {

    Page<AdminAuditEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
