package io.infraforge.domain;

/**
 * Authenticated user as resolved from a validated JWT.
 * Used as the Spring Security principal throughout the application.
 */
public record User(
        String userId,    // GitHub node ID — globally unique and stable
        String login,     // GitHub username (display only)
        String email
) {}
