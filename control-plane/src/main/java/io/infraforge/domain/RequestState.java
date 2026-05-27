package io.infraforge.domain;

import java.time.Instant;

/**
 * Sealed hierarchy representing every possible state an {@link InfraRequest} can be in.
 * <p>
 * Using a sealed interface + records gives exhaustive pattern matching at compile time —
 * the compiler will flag any switch expression that is missing a branch, making it
 * impossible to forget handling a new state when one is added to the hierarchy.
 * </p>
 */
public sealed interface RequestState
        permits RequestState.Submitted,
                RequestState.PrCreated,
                RequestState.PlanRunning,
                RequestState.PlanApproved,
                RequestState.Applying,
                RequestState.Deployed,
                RequestState.Failed {

    /** Returns the stable string name used for persistence and API serialisation. */
    default String typeName() {
        return switch (this) {
            case Submitted   ignored -> "SUBMITTED";
            case PrCreated   ignored -> "PR_CREATED";
            case PlanRunning ignored -> "PLAN_RUNNING";
            case PlanApproved ignored -> "PLAN_APPROVED";
            case Applying    ignored -> "APPLYING";
            case Deployed    ignored -> "DEPLOYED";
            case Failed      ignored -> "FAILED";
        };
    }

    // ── State records ─────────────────────────────────────────────────────────

    /** Request received from agent; workflow not yet started. */
    record Submitted() implements RequestState {}

    /** GitHub PR has been opened; waiting for CI to run. */
    record PrCreated(String prUrl, String branch) implements RequestState {}

    /** Terraform plan is running in GitHub Actions. */
    record PlanRunning(String checkRunId) implements RequestState {}

    /** Plan completed successfully; cost estimate available; awaiting approval. */
    record PlanApproved(double estimatedMonthlyCostUsd) implements RequestState {}

    /** terraform apply is running. */
    record Applying(String checkRunId) implements RequestState {}

    /** Infrastructure has been successfully provisioned. */
    record Deployed(Instant deployedAt) implements RequestState {}

    /** A terminal failure occurred at any stage. */
    record Failed(String reason) implements RequestState {}
}
