package io.infraforge.ports;

import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.RequestState;

import java.util.List;
import java.util.Optional;

/**
 * Cloud-agnostic port for persisting and querying {@link InfraRequest} state.
 *
 * <p>AWS implementation: DynamoDB (see {@code adapters.aws.DynamoDbStateStoreAdapter}).
 * Local implementation: in-memory ConcurrentHashMap (see {@code adapters.local.InMemoryStateStoreAdapter}).
 * Future: swap in a Firestore or CosmosDB adapter without touching the domain layer.</p>
 */
public interface StateStorePort {

    /** Persist a new request (insert semantics — throws if requestId already exists). */
    void save(InfraRequest request);

    /** Update an existing request — overwrites the full record. */
    void update(InfraRequest request);

    Optional<InfraRequest> findById(String requestId);

    /**
     * Return all requests for a given user, sorted by {@code createdAt} descending.
     * Implementations should page internally; callers receive the full (bounded) list.
     */
    List<InfraRequest> findByUserId(String userId);

    /** Convenience: transition to a new state and persist atomically. */
    default InfraRequest transition(String requestId, RequestState newState) {
        InfraRequest current = findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        InfraRequest updated = current.withState(newState);
        update(updated);
        return updated;
    }
}
