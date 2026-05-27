package io.infraforge.adapters.local;

import io.infraforge.domain.InfraRequest;
import io.infraforge.ports.StateStorePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link StateStorePort} for local development and tests.
 * Thread-safe via {@link ConcurrentHashMap}; not durable across restarts.
 */
@Component
@Profile("test")
public class InMemoryStateStoreAdapter implements StateStorePort {

    private final Map<String, InfraRequest> store = new ConcurrentHashMap<>();

    @Override
    public void save(InfraRequest request) {
        if (store.putIfAbsent(request.requestId(), request) != null) {
            throw new IllegalStateException("Request already exists: " + request.requestId());
        }
    }

    @Override
    public void update(InfraRequest request) {
        store.put(request.requestId(), request);
    }

    @Override
    public Optional<InfraRequest> findById(String requestId) {
        return Optional.ofNullable(store.get(requestId));
    }

    @Override
    public List<InfraRequest> findByUserId(String userId) {
        return store.values().stream()
                .filter(r -> userId.equals(r.userId()))
                .sorted(Comparator.comparing(InfraRequest::createdAt).reversed())
                .toList();
    }
}
