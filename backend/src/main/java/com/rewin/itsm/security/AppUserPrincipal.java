package com.rewin.itsm.security;

import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.user.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal.
 *
 * <p>Carries the user id and team id so ownership and team checks in the service
 * layer never need a second lookup. Roles are exposed as ROLE_* authorities,
 * which is what hasRole(...) expects.
 */
public class AppUserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final RoleName role;
    private final UUID teamId;
    private final boolean active;

    public AppUserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.teamId = user.getTeam() != null ? user.getTeam().getId() : null;
        this.active = user.isActive();
    }

    public UUID getId() {
        return id;
    }

    public RoleName getRole() {
        return role;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public boolean isAdmin() {
        return role == RoleName.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
