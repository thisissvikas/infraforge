package io.infraforge.domain;

import java.time.Instant;

/**
 * Message placed on the workflow queue by the API layer when a new request is submitted,
 * and by GitHub webhooks when CI completes.
 */
public record WorkflowEvent(
        String requestId,
        EventType type,
        String payload,   // JSON; schema depends on type
        Instant occurredAt
) {
    public enum EventType {
        REQUEST_SUBMITTED,
        PLAN_COMPLETED,
        PLAN_APPROVED,
        APPLY_COMPLETED,
        APPLY_FAILED
    }

    public static WorkflowEvent submitted(String requestId) {
        return new WorkflowEvent(requestId, EventType.REQUEST_SUBMITTED, "{}", Instant.now());
    }
}
