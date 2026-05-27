package io.infraforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.infraforge.api.dto.GitHubWebhookPayload;
import io.infraforge.domain.WorkflowEvent;
import io.infraforge.ports.MessageQueuePort;
import io.infraforge.ports.StateStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Receives webhooks from GitHub Actions.
 *
 * <p>GitHub sends a {@code check_run} event when a workflow job completes.
 * This controller validates the HMAC-SHA256 signature, maps the outcome to a
 * {@link WorkflowEvent}, and enqueues it for the workflow engine.</p>
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final String PLAN_CHECK_NAME  = "terraform-plan";
    private static final String APPLY_CHECK_NAME = "terraform-apply";

    private final MessageQueuePort queue;
    private final StateStorePort   stateStore;
    private final ObjectMapper     objectMapper;
    private final String           webhookSecret;
    private final String           workflowQueueUrl;

    public WebhookController(MessageQueuePort queue,
                              StateStorePort stateStore,
                              ObjectMapper objectMapper,
                              @Value("${infraforge.github.webhook-secret:}") String webhookSecret,
                              @Value("${infraforge.aws.sqs.workflow-queue-url:}") String workflowQueueUrl) {
        this.queue            = queue;
        this.stateStore       = stateStore;
        this.objectMapper     = objectMapper;
        this.webhookSecret    = webhookSecret;
        this.workflowQueueUrl = workflowQueueUrl;
    }

    /**
     * Receives the raw body as a String so we can both verify the HMAC signature
     * and deserialise the payload from the same bytes.
     */
    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestBody String rawBody) {

        if (!webhookSecret.isBlank() && !verifySignature(rawBody, signature)) {
            log.warn("GitHub webhook signature mismatch — rejecting");
            return ResponseEntity.status(401).build();
        }

        if (!"check_run".equals(event)) {
            return ResponseEntity.ok().build();
        }

        try {
            GitHubWebhookPayload payload = objectMapper.readValue(rawBody, GitHubWebhookPayload.class);
            if (!"completed".equals(payload.action())) {
                return ResponseEntity.ok().build();
            }
            processCheckRun(payload);
        } catch (Exception e) {
            log.error("Failed to parse GitHub webhook payload: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }

    private void processCheckRun(GitHubWebhookPayload payload) {
        String branch    = payload.checkRun().headBranch();
        String checkName = payload.checkRun().name();
        String conclusion = payload.checkRun().conclusion();

        // Branch name convention: infraforge/<requestId>
        if (branch == null || !branch.startsWith("infraforge/")) {
            return;
        }
        String requestId = branch.substring("infraforge/".length());

        WorkflowEvent.EventType type = switch (checkName) {
            case PLAN_CHECK_NAME  -> "success".equals(conclusion)
                    ? WorkflowEvent.EventType.PLAN_COMPLETED
                    : WorkflowEvent.EventType.APPLY_FAILED;
            case APPLY_CHECK_NAME -> "success".equals(conclusion)
                    ? WorkflowEvent.EventType.APPLY_COMPLETED
                    : WorkflowEvent.EventType.APPLY_FAILED;
            default -> null;
        };

        if (type == null) {
            return;
        }

        log.info("GitHub webhook: requestId={} check={} conclusion={} → event={}",
                requestId, checkName, conclusion, type);

        queue.publish(workflowQueueUrl, new WorkflowEvent(requestId, type, conclusion, java.time.Instant.now()));
    }

    private boolean verifySignature(String body, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256"));
            byte[] expected = mac.doFinal(body.getBytes());
            String expectedHex = "sha256=" + HexFormat.of().formatHex(expected);
            return expectedHex.equals(signatureHeader);
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }
}
