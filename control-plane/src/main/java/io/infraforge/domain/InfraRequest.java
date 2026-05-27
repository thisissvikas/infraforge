package io.infraforge.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Core domain object representing a single infrastructure provisioning request.
 * <p>
 * Immutable record — state transitions produce new instances rather than mutating
 * in place, making the history of state changes traceable and thread-safe.
 * </p>
 */
public record InfraRequest(
        String requestId,
        String userId,
        String userEmail,
        String teamId,
        CloudProvider targetCloud,           // AWS | GCP | AZURE — where the infra will live
        RequestState state,
        String rawIntent,
        String generatedTerraform,           // null until generate_node runs
        String githubPrUrl,                  // null until PR_CREATED
        String githubBranch,                 // null until PR_CREATED
        String errorMessage,                 // null unless FAILED
        double estimatedMonthlyCostUsd,
        Instant createdAt,
        Instant updatedAt
) {

    // ── Factory ───────────────────────────────────────────────────────────────

    public static InfraRequest create(String userId, String userEmail,
                                       String teamId, String rawIntent,
                                       CloudProvider targetCloud) {
        Instant now = Instant.now();
        return new InfraRequest(
                UUID.randomUUID().toString(),
                userId, userEmail, teamId,
                targetCloud,
                new RequestState.Submitted(),
                rawIntent,
                null, null, null, null,
                0.0,
                now, now
        );
    }

    // ── Transition helpers ────────────────────────────────────────────────────
    // Each helper returns a new InfraRequest with the updated field(s) and a
    // refreshed updatedAt timestamp, leaving all other fields unchanged.

    public InfraRequest withState(RequestState newState) {
        return new InfraRequest(requestId, userId, userEmail, teamId, targetCloud,
                newState, rawIntent, generatedTerraform,
                githubPrUrl, githubBranch, errorMessage,
                estimatedMonthlyCostUsd, createdAt, Instant.now());
    }

    public InfraRequest withTerraform(String terraform) {
        return new InfraRequest(requestId, userId, userEmail, teamId, targetCloud,
                state, rawIntent, terraform,
                githubPrUrl, githubBranch, errorMessage,
                estimatedMonthlyCostUsd, createdAt, Instant.now());
    }

    public InfraRequest withPr(String prUrl, String branch) {
        return new InfraRequest(requestId, userId, userEmail, teamId, targetCloud,
                new RequestState.PrCreated(prUrl, branch),
                rawIntent, generatedTerraform,
                prUrl, branch, errorMessage,
                estimatedMonthlyCostUsd, createdAt, Instant.now());
    }

    public InfraRequest withCostEstimate(double costUsd) {
        return new InfraRequest(requestId, userId, userEmail, teamId, targetCloud,
                state, rawIntent, generatedTerraform,
                githubPrUrl, githubBranch, errorMessage,
                costUsd, createdAt, Instant.now());
    }

    public InfraRequest asFailed(String reason) {
        return new InfraRequest(requestId, userId, userEmail, teamId, targetCloud,
                new RequestState.Failed(reason),
                rawIntent, generatedTerraform,
                githubPrUrl, githubBranch, reason,
                estimatedMonthlyCostUsd, createdAt, Instant.now());
    }
}
