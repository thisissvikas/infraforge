package io.infraforge.adapters.stub;

import io.infraforge.domain.InfraRequest;
import io.infraforge.ports.GitHubPrPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link GitHubPrPort} that logs the operation and returns a
 * hard-coded result. Used in all profiles until the real GitHub REST API adapter is available.
 *
 * @implNote Stub implementation — replaced by GitHubRestApiAdapter in Phase 5
 */
@Component
@Profile({"aws", "local", "test"})
public class StubGitHubPrAdapter implements GitHubPrPort {

    private static final Logger log = LoggerFactory.getLogger(StubGitHubPrAdapter.class);

    @Override
    public GitHubPrResult createPr(InfraRequest request) {
        log.info("[STUB] Creating GitHub PR for requestId={} intent={}", request.requestId(), request.rawIntent());
        return new GitHubPrResult(
                "https://github.com/stub/infra/pull/1",
                "infraforge/" + request.requestId()
        );
    }
}
