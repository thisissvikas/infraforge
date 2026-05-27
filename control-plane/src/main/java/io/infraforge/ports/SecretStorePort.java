package io.infraforge.ports;

import java.util.Map;

/**
 * Cloud-agnostic port for fetching secrets at runtime.
 *
 * <p>AWS implementation: Secrets Manager (see {@code adapters.aws.AwsSecretsAdapter}).
 * Local implementation: environment variables / application properties
 * (see {@code adapters.local.InMemorySecretStoreAdapter}).</p>
 */
public interface SecretStorePort {

    /**
     * Fetch a single secret string by name.
     * Implementations should cache with a short TTL (e.g. 5 minutes) to avoid
     * per-request API calls without risking stale secrets for too long.
     *
     * @throws SecretNotFoundException if the secret does not exist
     */
    String getSecret(String secretName);

    /**
     * Fetch a secret that stores a JSON key-value map (e.g. database credentials).
     *
     * @throws SecretNotFoundException if the secret does not exist
     */
    Map<String, String> getSecretMap(String secretName);

    class SecretNotFoundException extends RuntimeException {
        public SecretNotFoundException(String name) {
            super("Secret not found: " + name);
        }
    }
}
