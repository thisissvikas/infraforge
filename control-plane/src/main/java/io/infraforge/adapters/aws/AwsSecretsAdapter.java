package io.infraforge.adapters.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.infraforge.ports.SecretStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secrets Manager-backed implementation of {@link SecretStorePort}.
 * Caches values for {@code CACHE_TTL} to avoid per-request API calls.
 */
public class AwsSecretsAdapter implements SecretStorePort {

    private static final Logger log = LoggerFactory.getLogger(AwsSecretsAdapter.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final SecretsManagerClient client;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CachedSecret> cache = new ConcurrentHashMap<>();

    public AwsSecretsAdapter(SecretsManagerClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSecret(String secretName) {
        return fetchRaw(secretName);
    }

    @Override
    public Map<String, String> getSecretMap(String secretName) {
        String raw = fetchRaw(secretName);
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Secret is not a JSON map: " + secretName, e);
        }
    }

    private String fetchRaw(String secretName) {
        CachedSecret cached = cache.get(secretName);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        try {
            String value = client.getSecretValue(r -> r.secretId(secretName)).secretString();
            cache.put(secretName, new CachedSecret(value, Instant.now().plus(CACHE_TTL)));
            return value;
        } catch (ResourceNotFoundException e) {
            throw new SecretNotFoundException(secretName);
        } catch (Exception e) {
            log.error("Failed to fetch secret '{}': {}", secretName, e.getMessage());
            throw new SecretNotFoundException(secretName);
        }
    }

    private record CachedSecret(String value, Instant expiresAt) {}
}
