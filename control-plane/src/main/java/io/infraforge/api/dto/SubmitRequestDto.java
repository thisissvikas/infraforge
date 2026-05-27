package io.infraforge.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Sent by the agent to {@code POST /internal/requests} to submit a new request. */
public record SubmitRequestDto(
        @NotBlank String userId,
        @NotBlank String userEmail,
        @NotBlank String teamId,
        @NotBlank String rawIntent,
        @NotBlank String targetCloud,      // "AWS" | "GCP" | "AZURE"
        String generatedTerraform          // may be null if the agent submits before generation
) {}
