package io.infraforge.auth;

import io.infraforge.domain.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Provides the {@code POST /auth/token} endpoint consumed by the Next.js Auth.js
 * JWT callback ({@code ui/src/lib/auth.ts}).
 *
 * <p>Flow: GitHub OAuth completes in Auth.js → Auth.js JWT callback POSTs the
 * GitHub access token here → we verify it against GitHub's API, build a {@link User},
 * and issue a Control Plane JWT → Auth.js stores it in the encrypted session cookie.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final GitHubUserResolver gitHubUserResolver;

    public AuthController(JwtService jwtService, GitHubUserResolver gitHubUserResolver) {
        this.jwtService = jwtService;
        this.gitHubUserResolver = gitHubUserResolver;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> exchangeToken(
            @RequestBody TokenExchangeRequest body) {

        User user = gitHubUserResolver.resolve(body.githubAccessToken());
        String cpToken = jwtService.issue(user);
        long expiresAt = Instant.now().toEpochMilli() + jwtService.expirationMs();

        return ResponseEntity.ok(Map.of(
                "token",     cpToken,
                "expiresAt", expiresAt
        ));
    }

    public record TokenExchangeRequest(String githubAccessToken) {}
}
