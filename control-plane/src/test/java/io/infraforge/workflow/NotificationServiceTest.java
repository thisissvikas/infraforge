package io.infraforge.workflow;

import io.infraforge.domain.CloudProvider;
import io.infraforge.domain.InfraRequest;
import io.infraforge.ports.NotificationPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    @Test
    void exception_in_send_does_not_propagate() {
        NotificationPort notificationPort = mock(NotificationPort.class);
        doThrow(new RuntimeException("SES unavailable")).when(notificationPort).send(any());

        NotificationService service = new NotificationService(notificationPort);
        InfraRequest request = InfraRequest.create("u-1", "u@test.com", "team-1", "create bucket", CloudProvider.AWS);

        assertDoesNotThrow(() -> service.prCreated(request));
        assertDoesNotThrow(() -> service.planApproved(request));
        assertDoesNotThrow(() -> service.deployed(request));
        assertDoesNotThrow(() -> service.failed(request));
    }
}
