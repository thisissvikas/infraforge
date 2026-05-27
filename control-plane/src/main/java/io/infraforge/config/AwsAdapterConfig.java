package io.infraforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.infraforge.adapters.aws.AwsSecretsAdapter;
import io.infraforge.adapters.aws.DynamoDbStateStoreAdapter;
import io.infraforge.adapters.aws.EventBridgeEventBusAdapter;
import io.infraforge.adapters.aws.S3ObjectStorageAdapter;
import io.infraforge.adapters.aws.SesNotificationAdapter;
import io.infraforge.adapters.aws.SqsMessageQueueAdapter;
import io.infraforge.ports.EventBusPort;
import io.infraforge.ports.MessageQueuePort;
import io.infraforge.ports.NotificationPort;
import io.infraforge.ports.ObjectStoragePort;
import io.infraforge.ports.SecretStorePort;
import io.infraforge.ports.StateStorePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Wires AWS adapter implementations to the port interfaces when the {@code aws}
 * or {@code local} (LocalStack) profile is active.
 *
 * <p>To add GCP support: create {@code GcpAdapterConfig} annotated with
 * {@code @Profile("gcp")} and implement the same ports with GCP clients.
 * No domain or API code changes required.</p>
 */
@Configuration
@Profile({"aws", "local"})
public class AwsAdapterConfig {

    private final InfraforgeProperties props;

    public AwsAdapterConfig(InfraforgeProperties props) {
        this.props = props;
    }

    @Bean
    public StateStorePort stateStorePort(DynamoDbEnhancedClient enhanced, ObjectMapper mapper) {
        return new DynamoDbStateStoreAdapter(enhanced, props.aws().dynamodb().tableName(), mapper);
    }

    @Bean
    public MessageQueuePort messageQueuePort(SqsClient sqs, ObjectMapper mapper) {
        return new SqsMessageQueueAdapter(sqs, mapper);
    }

    @Bean
    public SecretStorePort secretStorePort(SecretsManagerClient secretsManager, ObjectMapper mapper) {
        return new AwsSecretsAdapter(secretsManager, mapper);
    }

    @Bean
    public EventBusPort eventBusPort(EventBridgeClient eventBridge, ObjectMapper mapper) {
        return new EventBridgeEventBusAdapter(eventBridge, mapper, props.aws().eventbridge().eventBusName());
    }

    @Bean
    public NotificationPort notificationPort(SesV2Client ses) {
        return new SesNotificationAdapter(ses, props.aws().ses().fromEmail());
    }

    @Bean
    public ObjectStoragePort objectStoragePort(S3Client s3) {
        return new S3ObjectStorageAdapter(s3);
    }
}
