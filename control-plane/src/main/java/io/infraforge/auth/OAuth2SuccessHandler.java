package io.infraforge.auth;

import io.infraforge.domain.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Called by Spring Security after a successful GitHub OAuth flow.
 * Issues a Control Plane JWT and redirects the browser back to the UI.
 *
 * <p>The JWT is set as an {@code httpOnly} cookie so it is not accessible
 * to JavaScript (XSS protection). The UI forwards it as a {@code Bearer} header
 * via the Next.js route handlers which have access to the cookie.</p>
 *
 * <p>Alternatively, the UI can receive the token via the Next.js {@code /auth/token}
 * endpoint called during the Auth.js JWT callback (see {@code ui/src/lib/auth.ts}).</p>
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final String COOKIE_NAME = "infraforge_token";

    private final JwtService jwtService;
    private final String uiBaseUrl;

    public OAuth2SuccessHandler(JwtService jwtService,
                                 @Value("${infraforge.ui-base-url:http://localhost:3000}") String uiBaseUrl) {
        this.jwtService = jwtService;
        this.uiBaseUrl = uiBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String userId = String.valueOf(oauthUser.getAttribute("id"));
        String login  = oauthUser.getAttribute("login");
        String email  = resolveEmail(oauthUser);

        User user = new User(userId, login, email);
        String token = jwtService.issue(user);

        // Set as httpOnly cookie — not readable by JavaScript.
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtService.expirationMs() / 1000));
        response.addCookie(cookie);

        response.sendRedirect(uiBaseUrl + "/chat");
    }

    private String resolveEmail(OAuth2User oauthUser) {
        // GitHub may not expose email if it is private; fall back to login@users.noreply.github.com
        String email = oauthUser.getAttribute("email");
        if (email == null || email.isBlank()) {
            String login = oauthUser.getAttribute("login");
            email = login + "@users.noreply.github.com";
        }
        return email;
    }
}
