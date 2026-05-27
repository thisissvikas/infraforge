package io.infraforge.domain;

import java.time.Instant;

/**
 * Immutable audit record published to the event bus on every request state transition.
 * Stored for compliance; consumed by downstream audit/SIEM systems.
 */
public record AuditEvent(
        String eventId,
        String requestId,
        String userId,
        String teamId,
        String fromState,
        String toState,
        String detail,    // human-readable description of what triggered the transition
        Instant occurredAt
) {}
