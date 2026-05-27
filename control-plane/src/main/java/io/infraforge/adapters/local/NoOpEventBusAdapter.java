package io.infraforge.adapters.local;

import io.infraforge.domain.AuditEvent;
import io.infraforge.ports.EventBusPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs audit events to stdout instead of publishing to EventBridge.
 * Used in the local and test profiles.
 */
public class NoOpEventBusAdapter implements EventBusPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEventBusAdapter.class);

    @Override
    public void publish(AuditEvent event) {
        log.info("[audit] request={} {}→{} detail={}",
                event.requestId(), event.fromState(), event.toState(), event.detail());
    }
}
