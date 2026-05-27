package io.infraforge.api;

import io.infraforge.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Returns the currently authenticated user's profile.
 * Used by the UI on startup to resolve the session.
 */
@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Map.of(
                "userId", principal.userId(),
                "email",  principal.email(),
                "login",  principal.login()
        );
    }
}
