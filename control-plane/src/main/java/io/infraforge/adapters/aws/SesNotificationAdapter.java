package io.infraforge.adapters.aws;

import io.infraforge.domain.NotificationMessage;
import io.infraforge.ports.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Component
@Profile({"aws", "local"})
public class SesNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SesNotificationAdapter.class);

    private final SesV2Client sesClient;
    private final String fromEmail;

    public SesNotificationAdapter(SesV2Client sesClient,
                                   @Value("${infraforge.aws.ses.from-email}") String fromEmail) {
        this.sesClient = sesClient;
        this.fromEmail = fromEmail;
    }

    @Override
    public void send(NotificationMessage message) {
        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(fromEmail)
                    .destination(Destination.builder()
                            .toAddresses(message.recipientEmail())
                            .build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder()
                                            .data(message.subject())
                                            .charset("UTF-8")
                                            .build())
                                    .body(Body.builder()
                                            .html(Content.builder()
                                                    .data(message.bodyHtml())
                                                    .charset("UTF-8")
                                                    .build())
                                            .text(Content.builder()
                                                    .data(message.bodyText())
                                                    .charset("UTF-8")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build());
        } catch (Exception e) {
            // Notification failure must not propagate — log and move on.
            log.error("Failed to send SES email to {}: {}", message.recipientEmail(), e.getMessage(), e);
        }
    }
}
