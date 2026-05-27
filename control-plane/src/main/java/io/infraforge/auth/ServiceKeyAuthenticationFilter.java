package io.infraforge.auth;

import io.infraforge.config.InfraforgeProperties;
import io.infraforge.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates the Chat Agent's service-to-service calls to {@code /internal/**}.
 *
 * <p>The agent includes its pre-shared key via {@code X-Service-Key: <key>}.
 * In production the key is injected from Secrets Manager on both sides at startup.
 * This is intentionally simple — internal endpoints are not reachable from the internet.</p>
 */
@Component
public class ServiceKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Service-Key";
    private static final User   AGENT_PRINCIPAL = new User("agent", "infraforge-agent", "");

    private final String expectedKey;

    public ServiceKeyAuthenticationFilter(InfraforgeProperties props) {
        this.expectedKey = props.serviceKey();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (expectedKey.equals(key)) {
            SecurityContextHolder.getContext()
                    .setAuthentication(new AuthenticatedUser(AGENT_PRINCIPAL));
        }
        chain.doFilter(request, response);
    }
}
