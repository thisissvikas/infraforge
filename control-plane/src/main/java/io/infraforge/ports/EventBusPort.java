package io.infraforge.ports;

import io.infraforge.domain.AuditEvent;

/**
 * Cloud-agnostic port for publishing audit events to a central event bus.
 * Subscribers (SIEM, compliance dashboards) consume from the bus independently.
 *
 * <p>AWS implementation: EventBridge (see {@code adapters.aws.EventBridgeEventBusAdapter}).
 * Local implementation: no-op logger (see {@code adapters.local.NoOpEventBusAdapter}).</p>
 */
public interface EventBusPort {

    /**
     * Publish an audit event.
     * Fire-and-forget — implementations should not block the calling thread.
     * Failures are logged but must not propagate to callers.
     */
    void publish(AuditEvent event);
}
