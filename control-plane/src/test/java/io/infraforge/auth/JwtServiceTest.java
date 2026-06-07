package io.infraforge.auth;

import io.infraforge.config.InfraforgeProperties;
import io.infraforge.domain.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new InfraforgeProperties(
            new InfraforgeProperties.Jwt("test-secret-key-min-32-chars-for-hmac256", 3600000L),
            "test-service-key",
            new InfraforgeProperties.Aws("us-east-1", "",
                    new InfraforgeProperties.Aws.DynamoDb("test-table"),
                    new InfraforgeProperties.Aws.Sqs(""),
                    new InfraforgeProperties.Aws.S3("test-bucket"),
                    new InfraforgeProperties.Aws.Ses("test@test.com"),
                    new InfraforgeProperties.Aws.EventBridge("test-bus")),
            new InfraforgeProperties.GitHub("", "", "infra")
    ));

    @Test
    void issueAndValidate_roundTrip() {
        User user = new User("12345", "octocat", "octocat@github.com");

        String token = jwtService.issue(user);
        Optional<User> resolved = jwtService.validate(token);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().userId()).isEqualTo("12345");
        assertThat(resolved.get().login()).isEqualTo("octocat");
        assertThat(resolved.get().email()).isEqualTo("octocat@github.com");
    }

    @Test
    void validate_returnsEmptyForTamperedToken() {
        User user = new User("67890", "dev", "dev@test.com");
        String token = jwtService.issue(user);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThat(jwtService.validate(tampered)).isEmpty();
    }

    @Test
    void validate_returnsEmptyForGarbage() {
        assertThat(jwtService.validate("not.a.jwt")).isEmpty();
    }
}
