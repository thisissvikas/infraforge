package io.infraforge.adapters.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.infraforge.domain.WorkflowEvent;
import io.infraforge.ports.MessageQueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * SQS-backed implementation of {@link MessageQueuePort}.
 * Uses virtual threads (Java 21+) for the polling loop so that blocking I/O
 * does not consume platform threads.
 */
public class SqsMessageQueueAdapter implements MessageQueuePort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SqsMessageQueueAdapter.class);
    private static final int LONG_POLL_SECONDS = 20;
    private static final int MAX_MESSAGES = 10;

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;
    // Virtual-thread executor — each message is handled on its own virtual thread.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public SqsMessageQueueAdapter(SqsClient sqs, ObjectMapper objectMapper) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String queueUrl, WorkflowEvent event) {
        try {
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize WorkflowEvent", e);
        }
    }

    @Override
    public void subscribe(String queueUrl, Consumer<WorkflowEvent> handler) {
        // Start a dedicated virtual-thread polling loop.
        executor.submit(() -> pollLoop(queueUrl, handler));
        log.info("SQS subscriber started on queue: {}", queueUrl);
    }

    private void pollLoop(String queueUrl, Consumer<WorkflowEvent> handler) {
        while (running.get()) {
            try {
                List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(MAX_MESSAGES)
                        .waitTimeSeconds(LONG_POLL_SECONDS)
                        .build()).messages();

                for (Message msg : messages) {
                    executor.submit(() -> processMessage(queueUrl, msg, handler));
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("SQS poll error on {}: {}", queueUrl, e.getMessage(), e);
                }
            }
        }
    }

    private void processMessage(String queueUrl, Message msg, Consumer<WorkflowEvent> handler) {
        try {
            WorkflowEvent event = objectMapper.readValue(msg.body(), WorkflowEvent.class);
            handler.accept(event);
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(msg.receiptHandle())
                    .build());
        } catch (Exception e) {
            log.error("Failed to process SQS message {}: {}", msg.messageId(), e.getMessage(), e);
            // Leave the message on the queue — SQS visibility timeout will redeliver it.
        }
    }

    @Override
    public void close() {
        running.set(false);
        executor.shutdownNow();
    }
}
