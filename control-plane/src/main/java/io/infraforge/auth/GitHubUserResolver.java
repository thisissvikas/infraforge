package io.infraforge.auth;

import io.infraforge.domain.User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Resolves a GitHub access token to a {@link User} by calling the GitHub API.
 * Used by {@link AuthController} to validate that the token is genuine before
 * issuing a Control Plane JWT.
 */
@Component
public class GitHubUserResolver {

    private final RestClient restClient;

    public GitHubUserResolver() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @SuppressWarnings("unchecked")
    public User resolve(String githubAccessToken) {
        Map<String, Object> githubUser = restClient.get()
                .uri("/user")
                .header("Authorization", "Bearer " + githubAccessToken)
                .retrieve()
                .body(Map.class);

        if (githubUser == null) {
            throw new IllegalArgumentException("GitHub returned empty user response");
        }

        String userId = String.valueOf(githubUser.get("id"));
        String login  = (String) githubUser.get("login");
        String email  = (String) githubUser.getOrDefault("email", login + "@users.noreply.github.com");
        if (email == null || email.isBlank()) {
            email = login + "@users.noreply.github.com";
        }

        return new User(userId, login, email);
    }
}
