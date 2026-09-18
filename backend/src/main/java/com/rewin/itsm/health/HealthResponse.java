package com.rewin.itsm.health;

import java.time.Instant;

/** Public shape of GET /api/health. A record, so no entity is ever exposed. */
public record HealthResponse(String status, String application, String version, Instant timestamp) {
}
