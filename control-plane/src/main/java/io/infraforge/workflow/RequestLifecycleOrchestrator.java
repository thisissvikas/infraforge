package io.infraforge.workflow;

import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.RequestState;
import io.infraforge.ports.GitHubPrPort;
import io.infraforge.ports.StateStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the lifecycle of an {@link InfraRequest} from SUBMITTED to PR_CREATED.
 * Handles idempotency by checking for the request before acting.
 */
@Service
public class RequestLifecycleOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RequestLifecycleOrchestrator.class);

    private final StateStorePort stateStore;
    private final GitHubPrPort gitHubPrPort;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public RequestLifecycleOrchestrator(StateStorePort stateStore,
                                         GitHubPrPort gitHubPrPort,
                                         AuditService auditService,
                                         NotificationService notificationService) {
        this.stateStore = stateStore;
        this.gitHubPrPort = gitHubPrPort;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /**
     * Handle a REQUEST_SUBMITTED event: create a GitHub PR and transition state to PR_CREATED.
     * If the request is not found, logs a warning and returns (idempotent).
     */
    public void handleSubmitted(String requestId) {
        var maybeRequest = stateStore.findById(requestId);
        if (maybeRequest.isEmpty()) {
            log.warn("handleSubmitted: request not found, skipping. requestId={}", requestId);
            return;
        }
        InfraRequest request = maybeRequest.get();
        try {
            GitHubPrPort.GitHubPrResult result = gitHubPrPort.createPr(request);
            InfraRequest updated = stateStore.transition(requestId,
                    new RequestState.PrCreated(result.prUrl(), result.branch()));
            auditService.record(updated, "SUBMITTED", "PR_CREATED", "PR created: " + result.prUrl());
            notificationService.prCreated(updated);
        } catch (Exception e) {
            log.error("handleSubmitted: failed to create PR for requestId={}: {}", requestId, e.getMessage(), e);
            InfraRequest failed = stateStore.transition(requestId, new RequestState.Failed(e.getMessage()));
            auditService.record(failed, "SUBMITTED", "FAILED", "PR creation failed: " + e.getMessage());
            notificationService.failed(failed);
        }
    }
}
