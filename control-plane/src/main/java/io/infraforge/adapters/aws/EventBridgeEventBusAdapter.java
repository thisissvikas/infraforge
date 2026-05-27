package io.infraforge.adapters.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.infraforge.domain.AuditEvent;
import io.infraforge.ports.EventBusPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

public class EventBridgeEventBusAdapter implements EventBusPort {

    private static final Logger log = LoggerFactory.getLogger(EventBridgeEventBusAdapter.class);
    private static final String EVENT_SOURCE = "io.infraforge.control-plane";
    private static final String DETAIL_TYPE  = "InfraRequestStateTransition";

    private final EventBridgeClient client;
    private final ObjectMapper objectMapper;
    private final String eventBusName;

    public EventBridgeEventBusAdapter(EventBridgeClient client,
                                       ObjectMapper objectMapper,
                                       @Value("${infraforge.aws.eventbridge.event-bus-name}") String eventBusName) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.eventBusName = eventBusName;
    }

    @Override
    public void publish(AuditEvent event) {
        try {
            String detail = objectMapper.writeValueAsString(event);
            client.putEvents(PutEventsRequest.builder()
                    .entries(PutEventsRequestEntry.builder()
                            .eventBusName(eventBusName)
                            .source(EVENT_SOURCE)
                            .detailType(DETAIL_TYPE)
                            .detail(detail)
                            .build())
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize AuditEvent for EventBridge: {}", e.getMessage(), e);
        } catch (Exception e) {
            // Never propagate — audit failure must not break the workflow.
            log.error("Failed to publish AuditEvent to EventBridge: {}", e.getMessage(), e);
        }
    }
}
