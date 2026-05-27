package io.infraforge.adapters.local;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.infraforge.ports.SecretStorePort;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory secret store for the {@code test} profile.
 * Seeds the JWT secret from application properties at startup so tests
 * never need Secrets Manager.
 */
@Component
@Profile("test")
public class InMemorySecretStoreAdapter implements SecretStorePort {

    private final Map<String, String> secrets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final String jwtSecret;

    public InMemorySecretStoreAdapter(ObjectMapper objectMapper,
                                      @Value("${infraforge.jwt.secret}") String jwtSecret) {
        this.objectMapper = objectMapper;
        this.jwtSecret    = jwtSecret;
    }

    @PostConstruct
    void seed() {
        secrets.put("infraforge/jwt-secret", jwtSecret);
    }

    /** Pre-seed additional secrets programmatically (useful in tests). */
    public void put(String name, String value) {
        secrets.put(name, value);
    }

    @Override
    public String getSecret(String secretName) {
        String val = secrets.get(secretName);
        if (val == null) {
            throw new SecretNotFoundException(secretName);
        }
        return val;
    }

    @Override
    public Map<String, String> getSecretMap(String secretName) {
        String raw = getSecret(secretName);
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Secret is not a JSON map: " + secretName, e);
        }
    }
}
