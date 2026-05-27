package io.infraforge.ports;

import io.infraforge.domain.WorkflowEvent;

import java.util.function.Consumer;

/**
 * Cloud-agnostic port for async message passing between the API layer and the
 * workflow engine.
 *
 * <p>AWS implementation: SQS (see {@code adapters.aws.SqsMessageQueueAdapter}).
 * Local implementation: in-process blocking queue (see {@code adapters.local.InMemoryMessageQueueAdapter}).</p>
 */
public interface MessageQueuePort {

    /**
     * Publish a workflow event to the named queue.
     * Delivery is at-least-once; consumers must be idempotent.
     */
    void publish(String queueName, WorkflowEvent event);

    /**
     * Register a blocking consumer on the named queue.
     * Implementations should call {@code handler} on a virtual thread per message.
     * This method is typically called once at application startup.
     */
    void subscribe(String queueName, Consumer<WorkflowEvent> handler);
}
