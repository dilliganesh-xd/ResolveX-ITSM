package com.rewin.itsm.domain.user;

import com.rewin.itsm.common.BaseEntity;
import com.rewin.itsm.domain.enums.RoleName;
import com.rewin.itsm.domain.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An account.
 *
 * <p>One role per user, stored as an enum column. V1 has exactly three fixed
 * roles and a user holds one of them, so a roles table plus a join table would
 * add a query to every authenticated request and buy nothing.
 *
 * <p>{@code passwordHash} only ever holds a BCrypt digest; a plain password
 * never reaches this class, and the field is never mapped into a DTO.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
    indexes = {
        @Index(name = "ix_users_team", columnList = "team_id"),
        @Index(name = "ix_users_role", columnList = "role")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private RoleName role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_users_team"))
    private Team team;

    public User(String email, String passwordHash, String name, RoleName role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
    }
}
