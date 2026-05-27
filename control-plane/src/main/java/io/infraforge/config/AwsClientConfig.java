package io.infraforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Instantiates AWS SDK v2 clients for the {@code aws} and {@code local} profiles.
 *
 * <p>A single bean per client covers both cases:
 * <ul>
 *   <li>When {@code infraforge.aws.endpoint-override} is non-empty (local profile), all
 *       clients point to LocalStack and use static {@code test/test} credentials.</li>
 *   <li>When empty (aws profile), clients use {@link DefaultCredentialsProvider} (IAM role
 *       → env vars → profile) and the configured region.</li>
 * </ul>
 * </p>
 */
@Configuration
@Profile({"aws", "local"})
public class AwsClientConfig {

    private final InfraforgeProperties props;

    public AwsClientConfig(InfraforgeProperties props) {
        this.props = props;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private boolean isLocalStack() {
        return props.aws().endpointOverride() != null && !props.aws().endpointOverride().isBlank();
    }

    private URI endpointUri() {
        return URI.create(props.aws().endpointOverride());
    }

    private Region region() {
        return isLocalStack() ? Region.US_EAST_1 : Region.of(props.aws().region());
    }

    private AwsCredentialsProvider credentials() {
        return isLocalStack()
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
                : DefaultCredentialsProvider.create();
    }

    // ── Clients ───────────────────────────────────────────────────────────────

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder().region(region()).credentialsProvider(credentials());
        if (isLocalStack()) builder.endpointOverride(endpointUri());
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    @Bean
    public SqsClient sqsClient() {
        var builder = SqsClient.builder().region(region()).credentialsProvider(credentials());
        if (isLocalStack()) builder.endpointOverride(endpointUri());
        return builder.build();
    }

    @Bean
    public SesV2Client sesV2Client() {
        var builder = SesV2Client.builder().region(region()).credentialsProvider(credentials());
        if (isLocalStack()) builder.endpointOverride(endpointUri());
        return builder.build();
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder().region(region()).credentialsProvider(credentials());
        if (isLocalStack()) {
            builder.endpointOverride(endpointUri()).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    public EventBridgeClient eventBridgeClient() {
        var builder = EventBridgeClient.builder().region(region()).credentialsProvider(credentials());
        if (isLocalStack()) builder.endpointOverride(endpointUri());
        return builder.build();
    }

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        var builder = SecretsManagerClient.builder().region(region()).credentialsProvider(credentials());
        if (isLocalStack()) builder.endpointOverride(endpointUri());
        return builder.build();
    }
}
