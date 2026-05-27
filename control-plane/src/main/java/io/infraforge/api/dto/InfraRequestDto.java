package io.infraforge.api.dto;

import io.infraforge.domain.InfraRequest;

/**
 * API representation of an {@link InfraRequest} returned to the UI.
 * Deliberately omits the full generated Terraform (large payload) — the UI
 * can fetch it separately if needed.
 */
public record InfraRequestDto(
        String requestId,
        String teamId,
        String rawIntent,
        String targetCloud,
        String state,
        String githubPrUrl,
        String githubBranch,
        double estimatedMonthlyCostUsd,
        String errorMessage,
        String createdAt,
        String updatedAt
) {
    public static InfraRequestDto from(InfraRequest r) {
        return new InfraRequestDto(
                r.requestId(),
                r.teamId(),
                r.rawIntent(),
                r.targetCloud().name(),
                r.state().typeName(),
                r.githubPrUrl(),
                r.githubBranch(),
                r.estimatedMonthlyCostUsd(),
                r.errorMessage(),
                r.createdAt().toString(),
                r.updatedAt().toString()
        );
    }
}
