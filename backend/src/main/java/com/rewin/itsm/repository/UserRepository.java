package com.rewin.itsm.repository;

import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findAllByRole(RoleName role);

    /** Only active agents are offered for new assignments. */
    List<User> findAllByRoleAndActiveTrueOrderByNameAsc(RoleName role);

    long countByActiveTrue();

    long countByTeamId(UUID teamId);

    List<User> findAllByTeamIdOrderByNameAsc(UUID teamId);
}
