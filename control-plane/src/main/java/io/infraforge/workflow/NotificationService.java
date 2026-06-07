package io.infraforge.workflow;

import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.NotificationMessage;
import io.infraforge.ports.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends developer notifications at key workflow milestones.
 * Never throws — failures are logged as warnings.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationPort notificationPort;

    public NotificationService(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    public void prCreated(InfraRequest req) {
        String prUrl = req.githubPrUrl() != null ? req.githubPrUrl() : "(unknown)";
        send(req, "infraforge: PR Created",
                "Your infrastructure request has a new PR: " + prUrl,
                "Your infrastructure request has a new PR: " + prUrl);
    }

    public void planApproved(InfraRequest req) {
        String cost = String.format("%.2f", req.estimatedMonthlyCostUsd());
        send(req, "infraforge: Plan Approved",
                "Your Terraform plan has been approved. Cost estimate: $" + cost + "/mo",
                "Your Terraform plan has been approved. Cost estimate: $" + cost + "/mo");
    }

    public void deployed(InfraRequest req) {
        send(req, "infraforge: Deployed!",
                "Your infrastructure has been deployed successfully.",
                "Your infrastructure has been deployed successfully.");
    }

    public void failed(InfraRequest req) {
        String error = req.errorMessage() != null ? req.errorMessage() : "(unknown error)";
        send(req, "infraforge: Request Failed",
                "Your infrastructure request failed: " + error,
                "Your infrastructure request failed: " + error);
    }

    private void send(InfraRequest req, String subject, String bodyHtml, String bodyText) {
        try {
            NotificationMessage message = new NotificationMessage(
                    req.userEmail(),
                    subject,
                    bodyHtml,
                    bodyText
            );
            notificationPort.send(message);
        } catch (Exception e) {
            log.warn("Failed to send notification '{}' for request={}: {}", subject, req.requestId(), e.getMessage());
        }
    }
}
