package com.rewin.itsm.domain.team;

import com.rewin.itsm.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A support team an incident can be routed to. */
@Entity
@Table(name = "teams", uniqueConstraints = @UniqueConstraint(name = "uk_teams_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 240)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Team(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
