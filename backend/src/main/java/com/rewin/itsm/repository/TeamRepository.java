package com.rewin.itsm.repository;

import com.rewin.itsm.domain.team.Team;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
