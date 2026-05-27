package io.infraforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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
 * Instantiates AWS SDK v2 clients.
 *
 * <p>The {@code aws} profile creates standard production clients using the
 * {@link DefaultCredentialsProvider} chain (IAM role → env vars → profile).
 * The {@code local} profile uses LocalStack via {@code AWS_ENDPOINT_URL}
 * (overridden in application-local.yml).</p>
 */
@Configuration
public class AwsClientConfig {

    private final InfraforgeProperties props;

    public AwsClientConfig(InfraforgeProperties props) {
        this.props = props;
    }

    @Bean
    @Profile("aws")
    public DynamoDbClient dynamoDbClientAws() {
        return DynamoDbClient.builder()
                .region(Region.of(props.aws().region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Profile("local")
    public DynamoDbClient dynamoDbClientLocal() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(() -> software.amazon.awssdk.auth.credentials
                        .AwsBasicCredentials.create("test", "test"))
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    @Bean
    @Profile("aws")
    public SqsClient sqsClientAws() {
        return SqsClient.builder()
                .region(Region.of(props.aws().region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Profile("local")
    public SqsClient sqsClientLocal() {
        return SqsClient.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(() -> software.amazon.awssdk.auth.credentials
                        .AwsBasicCredentials.create("test", "test"))
                .build();
    }

    @Bean
    @Profile("aws")
    public SesV2Client sesClientAws() {
        return SesV2Client.builder()
                .region(Region.of(props.aws().region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Profile("local")
    public SesV2Client sesClientLocal() {
        return SesV2Client.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(() -> software.amazon.awssdk.auth.credentials
                        .AwsBasicCredentials.create("test", "test"))
                .build();
    }

    @Bean
    @Profile("aws")
    public S3Client s3ClientAws() {
        return S3Client.builder()
                .region(Region.of(props.aws().region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Profile("local")
    public S3Client s3ClientLocal() {
        return S3Client.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .forcePathStyle(true)
                .region(Region.US_EAST_1)
                .credentialsProvider(() -> software.amazon.awssdk.auth.credentials
                        .AwsBasicCredentials.create("test", "test"))
                .build();
    }

    @Bean
    @Profile("aws")
    public EventBridgeClient eventBridgeClientAws() {
        return EventBridgeClient.builder()
                .region(Region.of(props.aws().region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Profile("local")
    public EventBridgeClient eventBridgeClientLocal() {
        return EventBridgeClient.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(() -> software.amazon.awssdk.auth.credentials
                        .AwsBasicCredentials.create("test", "test"))
                .build();
    }

    @Bean
    @Profile("aws")
    public SecretsManagerClient secretsManagerClientAws() {
        return SecretsManagerClient.builder()
                .region(Region.of(props.aws().region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Profile("local")
    public SecretsManagerClient secretsManagerClientLocal() {
        return SecretsManagerClient.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(() -> software.amazon.awssdk.auth.credentials
                        .AwsBasicCredentials.create("test", "test"))
                .build();
    }
}
