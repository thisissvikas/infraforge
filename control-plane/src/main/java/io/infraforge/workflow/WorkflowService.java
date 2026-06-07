package io.infraforge.workflow;

import io.infraforge.config.InfraforgeProperties;
import io.infraforge.domain.WorkflowEvent;
import io.infraforge.ports.MessageQueuePort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Entry point for the workflow engine. Subscribes to the SQS/in-memory queue at startup
 * and dispatches each {@link WorkflowEvent} to the appropriate handler.
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final MessageQueuePort messageQueuePort;
    private final InfraforgeProperties props;
    private final RequestLifecycleOrchestrator orchestrator;
    private final CiMonitorService ciMonitor;

    public WorkflowService(MessageQueuePort messageQueuePort,
                           InfraforgeProperties props,
                           RequestLifecycleOrchestrator orchestrator,
                           CiMonitorService ciMonitor) {
        this.messageQueuePort = messageQueuePort;
        this.props = props;
        this.orchestrator = orchestrator;
        this.ciMonitor = ciMonitor;
    }

    @PostConstruct
    public void startConsumer() {
        messageQueuePort.subscribe(props.aws().sqs().workflowQueueUrl(), this::dispatch);
        log.info("WorkflowService: consumer registered on queue '{}'", props.aws().sqs().workflowQueueUrl());
    }

    void dispatch(WorkflowEvent event) {
        try {
            switch (event.type()) {
                case REQUEST_SUBMITTED -> orchestrator.handleSubmitted(event.requestId());
                case PLAN_COMPLETED -> ciMonitor.handlePlanCompleted(event.requestId(), event.payload());
                case PLAN_APPROVED -> ciMonitor.handlePlanCompleted(event.requestId(), event.payload());
                case APPLY_COMPLETED -> ciMonitor.handleApplyCompleted(event.requestId());
                case APPLY_FAILED -> ciMonitor.handleApplyFailed(event.requestId(), event.payload());
            }
        } catch (Exception e) {
            log.error("WorkflowService.dispatch: unhandled error for event type={} requestId={}: {}",
                    event.type(), event.requestId(), e.getMessage(), e);
        }
    }
}
