package io.infraforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.infraforge.adapters.local.InMemoryMessageQueueAdapter;
import io.infraforge.adapters.local.InMemoryObjectStorageAdapter;
import io.infraforge.adapters.local.InMemorySecretStoreAdapter;
import io.infraforge.adapters.local.InMemoryStateStoreAdapter;
import io.infraforge.adapters.local.LoggingNotificationAdapter;
import io.infraforge.adapters.local.NoOpEventBusAdapter;
import io.infraforge.ports.EventBusPort;
import io.infraforge.ports.MessageQueuePort;
import io.infraforge.ports.NotificationPort;
import io.infraforge.ports.ObjectStoragePort;
import io.infraforge.ports.SecretStorePort;
import io.infraforge.ports.StateStorePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires in-memory (no AWS) adapters for the {@code test} profile.
 * Also used in unit tests where no Spring context profile is set.
 *
 * <p>The {@code local} profile uses {@link AwsAdapterConfig} against LocalStack.
 * The {@code test} profile uses this config — zero external dependencies.</p>
 */
@Configuration
@Profile("test")
public class LocalAdapterConfig {

    private final InfraforgeProperties props;

    public LocalAdapterConfig(InfraforgeProperties props) {
        this.props = props;
    }

    @Bean
    public StateStorePort stateStorePort() {
        return new InMemoryStateStoreAdapter();
    }

    @Bean
    public MessageQueuePort messageQueuePort() {
        return new InMemoryMessageQueueAdapter();
    }

    @Bean
    public SecretStorePort secretStorePort(ObjectMapper mapper) {
        InMemorySecretStoreAdapter adapter = new InMemorySecretStoreAdapter(mapper);
        // Seed the JWT secret from properties so tests don't need Secrets Manager.
        adapter.put("infraforge/jwt-secret", props.jwt().secret());
        return adapter;
    }

    @Bean
    public EventBusPort eventBusPort() {
        return new NoOpEventBusAdapter();
    }

    @Bean
    public NotificationPort notificationPort() {
        return new LoggingNotificationAdapter();
    }

    @Bean
    public ObjectStoragePort objectStoragePort() {
        return new InMemoryObjectStorageAdapter();
    }
}
