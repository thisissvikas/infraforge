package io.infraforge.adapters.local;

import io.infraforge.domain.NotificationMessage;
import io.infraforge.ports.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs notification content to stdout instead of sending email via SES.
 * Used in the local and test profiles.
 */
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void send(NotificationMessage message) {
        log.info("[notification] to={} subject={}\n{}",
                message.recipientEmail(), message.subject(), message.bodyText());
    }
}
