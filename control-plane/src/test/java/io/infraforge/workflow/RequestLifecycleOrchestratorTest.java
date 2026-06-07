package io.infraforge.workflow;

import io.infraforge.domain.CloudProvider;
import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.RequestState;
import io.infraforge.ports.GitHubPrPort;
import io.infraforge.ports.StateStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RequestLifecycleOrchestratorTest {

    private StateStorePort stateStore;
    private GitHubPrPort gitHubPrPort;
    private AuditService auditService;
    private NotificationService notificationService;
    private RequestLifecycleOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        stateStore = mock(StateStorePort.class);
        gitHubPrPort = mock(GitHubPrPort.class);
        auditService = mock(AuditService.class);
        notificationService = mock(NotificationService.class);
        orchestrator = new RequestLifecycleOrchestrator(stateStore, gitHubPrPort, auditService, notificationService);
    }

    @Test
    void happyPath_submittedToPrCreated() {
        InfraRequest request = InfraRequest.create("u-1", "u@test.com", "team-1", "create VPC", CloudProvider.AWS);
        String prUrl = "https://github.com/stub/infra/pull/1";
        String branch = "infraforge/" + request.requestId();

        when(stateStore.findById(request.requestId())).thenReturn(Optional.of(request));
        when(gitHubPrPort.createPr(request)).thenReturn(new GitHubPrPort.GitHubPrResult(prUrl, branch));
        doNothing().when(stateStore).update(any(InfraRequest.class));

        orchestrator.handleSubmitted(request.requestId());

        verify(stateStore).update(argThat(r ->
                r.githubPrUrl().equals(prUrl) && r.githubBranch().equals(branch)
                && r.state() instanceof RequestState.PrCreated));
        verify(auditService).record(
                argThat(r -> r.githubPrUrl().equals(prUrl)),
                eq("SUBMITTED"), eq("PR_CREATED"), any(String.class));
        verify(notificationService).prCreated(argThat(r -> r.githubPrUrl().equals(prUrl)));
    }

    @Test
    void prCreation_fails_transitions_to_failed() {
        InfraRequest request = InfraRequest.create("u-1", "u@test.com", "team-1", "create VPC", CloudProvider.AWS);
        InfraRequest failedRequest = request.asFailed("GitHub API error");

        when(stateStore.findById(request.requestId())).thenReturn(Optional.of(request));
        when(gitHubPrPort.createPr(request)).thenThrow(new RuntimeException("GitHub API error"));
        when(stateStore.transition(eq(request.requestId()), any(RequestState.Failed.class))).thenReturn(failedRequest);

        orchestrator.handleSubmitted(request.requestId());

        verify(stateStore).transition(eq(request.requestId()), any(RequestState.Failed.class));
        verify(notificationService).failed(failedRequest);
    }

    @Test
    void handleSubmitted_requestNotFound_logsAndReturns() {
        when(stateStore.findById("missing-id")).thenReturn(Optional.empty());

        orchestrator.handleSubmitted("missing-id");

        verifyNoInteractions(gitHubPrPort);
        verifyNoInteractions(auditService);
        verifyNoInteractions(notificationService);
    }
}
