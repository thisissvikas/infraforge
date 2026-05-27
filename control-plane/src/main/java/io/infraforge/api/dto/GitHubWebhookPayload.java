package io.infraforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal subset of a GitHub Actions {@code check_run} webhook payload.
 * Spring's Jackson deserialises the incoming JSON into this record.
 */
public record GitHubWebhookPayload(
        String action,
        @JsonProperty("check_run") CheckRun checkRun,
        Repository repository
) {
    public record CheckRun(
            long id,
            String name,
            String status,
            String conclusion,
            @JsonProperty("head_branch") String headBranch,
            @JsonProperty("html_url") String htmlUrl
    ) {}

    public record Repository(
            String name,
            @JsonProperty("full_name") String fullName
    ) {}
}
