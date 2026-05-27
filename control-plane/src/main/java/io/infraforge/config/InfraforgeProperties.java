package io.infraforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strongly-typed binding for all {@code infraforge.*} configuration properties.
 */
@ConfigurationProperties(prefix = "infraforge")
public record InfraforgeProperties(
        Jwt jwt,
        String serviceKey,
        Aws aws,
        GitHub github
) {
    public record Jwt(
            String secret,
            @DefaultValue("86400000") long expirationMs
    ) {}

    public record Aws(
            @DefaultValue("us-east-1") String region,
            DynamoDb dynamodb,
            Sqs sqs,
            S3 s3,
            Ses ses,
            EventBridge eventbridge
    ) {
        public record DynamoDb(@DefaultValue("infraforge-requests") String tableName) {}
        public record Sqs(@DefaultValue("") String workflowQueueUrl) {}
        public record S3(@DefaultValue("infraforge-terraform") String terraformBucket) {}
        public record Ses(@DefaultValue("noreply@infraforge.io") String fromEmail) {}
        public record EventBridge(@DefaultValue("infraforge-audit") String eventBusName) {}
    }

    public record GitHub(
            String appInstallationToken,
            @DefaultValue("") String org,
            @DefaultValue("infra") String infraRepo
    ) {}
}
