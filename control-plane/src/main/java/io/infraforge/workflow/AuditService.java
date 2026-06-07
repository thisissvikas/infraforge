package io.infraforge.workflow;

import io.infraforge.domain.AuditEvent;
import io.infraforge.domain.InfraRequest;
import io.infraforge.ports.EventBusPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Records audit events for every request state transition.
 * Never throws — failures are logged as warnings.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final EventBusPort eventBusPort;

    public AuditService(EventBusPort eventBusPort) {
        this.eventBusPort = eventBusPort;
    }

    /**
     * Publish an audit event for a state transition. This method never propagates exceptions.
     */
    public void record(InfraRequest req, String fromState, String toState, String detail) {
        try {
            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    req.requestId(),
                    req.userId(),
                    req.teamId(),
                    fromState,
                    toState,
                    detail,
                    Instant.now()
            );
            eventBusPort.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish audit event for request={} transition={}→{}: {}",
                    req.requestId(), fromState, toState, e.getMessage());
        }
    }
}
