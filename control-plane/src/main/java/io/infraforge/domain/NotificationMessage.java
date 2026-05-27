package io.infraforge.domain;

/**
 * Notification sent to the developer when their request reaches a significant state.
 */
public record NotificationMessage(
        String recipientEmail,
        String subject,
        String bodyHtml,
        String bodyText   // plain-text fallback
) {}
