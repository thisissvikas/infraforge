package io.infraforge.adapters.local;

import io.infraforge.domain.CloudProvider;
import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.RequestState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryStateStoreAdapterTest {

    private final InMemoryStateStoreAdapter store = new InMemoryStateStoreAdapter();

    @Test
    void saveAndFindById() {
        InfraRequest req = InfraRequest.create("user1", "user1@test.com", "team-a", "I need an S3 bucket", CloudProvider.AWS);
        store.save(req);

        assertThat(store.findById(req.requestId()))
                .isPresent()
                .get()
                .extracting(InfraRequest::rawIntent)
                .isEqualTo("I need an S3 bucket");
    }

    @Test
    void findByUserId_returnsSortedByCreatedAtDesc() throws InterruptedException {
        InfraRequest r1 = InfraRequest.create("user2", "u@t.com", "team-b", "first", CloudProvider.AWS);
        Thread.sleep(5); // ensure different timestamps
        InfraRequest r2 = InfraRequest.create("user2", "u@t.com", "team-b", "second", CloudProvider.AWS);

        store.save(r1);
        store.save(r2);

        List<InfraRequest> results = store.findByUserId("user2");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).rawIntent()).isEqualTo("second"); // most recent first
    }

    @Test
    void transitionState() {
        InfraRequest req = InfraRequest.create("user3", "u@t.com", "team-c", "ECS service", CloudProvider.AWS);
        store.save(req);

        InfraRequest updated = store.transition(req.requestId(),
                new RequestState.PrCreated("https://github.com/org/infra/pull/1", "infraforge/" + req.requestId()));

        assertThat(updated.state()).isInstanceOf(RequestState.PrCreated.class);
        assertThat(store.findById(req.requestId()).get().state()).isInstanceOf(RequestState.PrCreated.class);
    }

    @Test
    void save_throwsOnDuplicateRequestId() {
        InfraRequest req = InfraRequest.create("user4", "u@t.com", "team-d", "duplicate", CloudProvider.AWS);
        store.save(req);

        assertThatThrownBy(() -> store.save(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void findById_returnsEmptyForUnknown() {
        assertThat(store.findById("nonexistent")).isEmpty();
    }
}
