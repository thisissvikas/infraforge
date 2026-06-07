package io.infraforge.workflow;

import io.infraforge.domain.CloudProvider;
import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.RequestState;
import io.infraforge.domain.WorkflowEvent;
import io.infraforge.ports.MessageQueuePort;
import io.infraforge.ports.StateStorePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowServiceTest {

    @Autowired
    MessageQueuePort messageQueuePort;

    @Autowired
    StateStorePort stateStore;

    @Autowired
    WorkflowService workflowService;

    @Test
    void dispatches_requestSubmitted_event() throws InterruptedException {
        InfraRequest request = InfraRequest.create("u-wf-test", "wf@test.com", "team-wf", "create S3 bucket", CloudProvider.AWS);
        stateStore.save(request);

        messageQueuePort.publish("", WorkflowEvent.submitted(request.requestId()));

        // Allow up to 2 seconds for the async consumer to process
        long deadline = System.currentTimeMillis() + 2000;
        InfraRequest result = request;
        while (System.currentTimeMillis() < deadline) {
            result = stateStore.findById(request.requestId()).orElse(request);
            if (result.state() instanceof RequestState.PrCreated) {
                break;
            }
            Thread.sleep(100);
        }

        assertThat(result.state()).isInstanceOf(RequestState.PrCreated.class);
    }

    @Test
    void dispatch_withUnknownRequest_doesNotCrash() {
        // Dispatching an event for a non-existent request should log + return without crashing
        workflowService.dispatch(WorkflowEvent.submitted("nonexistent-request-id-xyz"));
        // If we get here without exception, the test passes
    }
}
