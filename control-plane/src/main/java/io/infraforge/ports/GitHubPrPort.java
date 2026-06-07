package io.infraforge.ports;

import io.infraforge.domain.InfraRequest;

/**
 * Port for interacting with GitHub to create pull requests for generated Terraform code.
 *
 * <p>AWS/real implementation: GitHubRestApiAdapter (Phase 5).
 * Stub implementation: StubGitHubPrAdapter.</p>
 */
public interface GitHubPrPort {

    /**
     * Create a GitHub pull request for the given infrastructure request.
     *
     * @param request the infra request containing generated Terraform and metadata
     * @return result containing the PR URL and branch name
     */
    GitHubPrResult createPr(InfraRequest request);

    record GitHubPrResult(String prUrl, String branch) {}
}
