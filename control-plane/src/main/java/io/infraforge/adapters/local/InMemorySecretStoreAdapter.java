package io.infraforge.adapters.local;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.infraforge.ports.SecretStorePort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory secret store for local development.
 * Secrets are pre-seeded programmatically in {@code LocalAdapterConfig}
 * from application properties, so no Secrets Manager is needed locally.
 */
public class InMemorySecretStoreAdapter implements SecretStorePort {

    private final Map<String, String> secrets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public InMemorySecretStoreAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Pre-seed a secret — called from LocalAdapterConfig at startup. */
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
