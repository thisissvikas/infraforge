package io.infraforge.workflow;

import io.infraforge.domain.CloudProvider;
import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.RequestState;
import io.infraforge.ports.StateStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CiMonitorServiceTest {

    private StateStorePort stateStore;
    private AuditService auditService;
    private NotificationService notificationService;
    private CiMonitorService ciMonitor;

    @BeforeEach
    void setUp() {
        stateStore = mock(StateStorePort.class);
        auditService = mock(AuditService.class);
        notificationService = mock(NotificationService.class);
        ciMonitor = new CiMonitorService(stateStore, auditService, notificationService);
    }

    private InfraRequest sampleRequest() {
        return InfraRequest.create("u-ci", "ci@test.com", "team-ci", "create RDS", CloudProvider.AWS);
    }

    @Test
    void handlePlanCompleted_transitionsToPlanApproved() {
        InfraRequest request = sampleRequest();
        InfraRequest updated = request.withState(new RequestState.PlanApproved(0.0));

        when(stateStore.findById(request.requestId())).thenReturn(Optional.of(request));
        when(stateStore.transition(eq(request.requestId()), any(RequestState.PlanApproved.class))).thenReturn(updated);

        ciMonitor.handlePlanCompleted(request.requestId(), "{}");

        verify(stateStore).transition(eq(request.requestId()), any(RequestState.PlanApproved.class));
        verify(auditService).record(eq(updated), eq("PLAN_RUNNING"), eq("PLAN_APPROVED"), any(String.class));
        verify(notificationService).planApproved(updated);
    }

    @Test
    void handleApplyCompleted_transitionsToDeployed() {
        InfraRequest request = sampleRequest();
        InfraRequest updated = request.withState(new RequestState.Deployed(java.time.Instant.now()));

        when(stateStore.findById(request.requestId())).thenReturn(Optional.of(request));
        when(stateStore.transition(eq(request.requestId()), any(RequestState.Deployed.class))).thenReturn(updated);

        ciMonitor.handleApplyCompleted(request.requestId());

        verify(stateStore).transition(eq(request.requestId()), any(RequestState.Deployed.class));
        verify(auditService).record(eq(updated), eq("APPLYING"), eq("DEPLOYED"), any(String.class));
        verify(notificationService).deployed(updated);
    }

    @Test
    void handleApplyFailed_transitionsToFailed() {
        InfraRequest request = sampleRequest();
        InfraRequest updated = request.asFailed("timeout");

        when(stateStore.findById(request.requestId())).thenReturn(Optional.of(request));
        when(stateStore.transition(eq(request.requestId()), any(RequestState.Failed.class))).thenReturn(updated);

        ciMonitor.handleApplyFailed(request.requestId(), "timeout");

        verify(stateStore).transition(eq(request.requestId()), any(RequestState.Failed.class));
        verify(auditService).record(eq(updated), eq("APPLYING"), eq("FAILED"), any(String.class));
        verify(notificationService).failed(updated);
    }

    @Test
    void handlePlanCompleted_requestNotFound_logsAndReturns() {
        when(stateStore.findById("missing")).thenReturn(Optional.empty());

        ciMonitor.handlePlanCompleted("missing", "{}");

        verify(stateStore, never()).transition(any(), any());
        verifyNoInteractions(auditService, notificationService);
    }
}
