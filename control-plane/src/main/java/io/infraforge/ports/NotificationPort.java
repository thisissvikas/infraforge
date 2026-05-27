package io.infraforge.ports;

import io.infraforge.domain.NotificationMessage;

/**
 * Cloud-agnostic port for sending developer notifications (email for now;
 * Slack/webhook in the future via a different adapter).
 *
 * <p>AWS implementation: SES v2 (see {@code adapters.aws.SesNotificationAdapter}).
 * Local implementation: logs to stdout (see {@code adapters.local.LoggingNotificationAdapter}).</p>
 */
public interface NotificationPort {

    /**
     * Send a notification to a developer.
     * Implementations should be non-blocking where possible.
     * A failure to notify must never fail the calling workflow transition.
     */
    void send(NotificationMessage message);
}
