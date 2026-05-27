package io.infraforge.adapters.local;

import io.infraforge.domain.WorkflowEvent;
import io.infraforge.ports.MessageQueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * In-process, in-memory queue for local development.
 * Backed by a {@link LinkedBlockingQueue}; uses virtual threads for consumption.
 */
@Component
@Profile("test")
public class InMemoryMessageQueueAdapter implements MessageQueuePort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMessageQueueAdapter.class);

    private final BlockingQueue<WorkflowEvent> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);

    @Override
    public void publish(String queueName, WorkflowEvent event) {
        queue.offer(event);
        log.debug("[local-queue] Published {} for request {}", event.type(), event.requestId());
    }

    @Override
    public void subscribe(String queueName, Consumer<WorkflowEvent> handler) {
        executor.submit(() -> {
            log.info("[local-queue] Subscriber started on virtual-thread queue '{}'", queueName);
            while (running.get()) {
                try {
                    WorkflowEvent event = queue.take(); // blocks until available
                    executor.submit(() -> {
                        try {
                            handler.accept(event);
                        } catch (Exception e) {
                            log.error("[local-queue] Handler error for {}: {}", event.requestId(), e.getMessage(), e);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    @Override
    public void close() {
        running.set(false);
        executor.shutdownNow();
    }
}
