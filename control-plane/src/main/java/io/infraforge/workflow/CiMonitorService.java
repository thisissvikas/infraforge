package io.infraforge.workflow;

import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.RequestState;
import io.infraforge.ports.StateStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Handles CI lifecycle events from GitHub Actions: plan completion, apply completion, and apply failure.
 */
@Service
public class CiMonitorService {

    private static final Logger log = LoggerFactory.getLogger(CiMonitorService.class);

    private final StateStorePort stateStore;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public CiMonitorService(StateStorePort stateStore,
                             AuditService auditService,
                             NotificationService notificationService) {
        this.stateStore = stateStore;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /**
     * Handle PLAN_COMPLETED: auto-approve the plan and transition to PLAN_APPROVED.
     */
    public void handlePlanCompleted(String requestId, String payload) {
        var maybeRequest = stateStore.findById(requestId);
        if (maybeRequest.isEmpty()) {
            log.warn("handlePlanCompleted: request not found, skipping. requestId={}", requestId);
            return;
        }
        try {
            InfraRequest updated = stateStore.transition(requestId, new RequestState.PlanApproved(0.0));
            auditService.record(updated, "PLAN_RUNNING", "PLAN_APPROVED", "Plan completed; auto-approved");
            notificationService.planApproved(updated);
        } catch (Exception e) {
            log.error("handlePlanCompleted: error for requestId={}: {}", requestId, e.getMessage(), e);
        }
    }

    /**
     * Handle APPLY_COMPLETED: transition to DEPLOYED.
     */
    public void handleApplyCompleted(String requestId) {
        var maybeRequest = stateStore.findById(requestId);
        if (maybeRequest.isEmpty()) {
            log.warn("handleApplyCompleted: request not found, skipping. requestId={}", requestId);
            return;
        }
        try {
            InfraRequest updated = stateStore.transition(requestId, new RequestState.Deployed(Instant.now()));
            auditService.record(updated, "APPLYING", "DEPLOYED", "Apply completed successfully");
            notificationService.deployed(updated);
        } catch (Exception e) {
            log.error("handleApplyCompleted: error for requestId={}: {}", requestId, e.getMessage(), e);
        }
    }

    /**
     * Handle APPLY_FAILED: transition to FAILED.
     */
    public void handleApplyFailed(String requestId, String reason) {
        var maybeRequest = stateStore.findById(requestId);
        if (maybeRequest.isEmpty()) {
            log.warn("handleApplyFailed: request not found, skipping. requestId={}", requestId);
            return;
        }
        try {
            InfraRequest updated = stateStore.transition(requestId, new RequestState.Failed(reason));
            auditService.record(updated, "APPLYING", "FAILED", "Apply failed: " + reason);
            notificationService.failed(updated);
        } catch (Exception e) {
            log.error("handleApplyFailed: error for requestId={}: {}", requestId, e.getMessage(), e);
        }
    }
}
