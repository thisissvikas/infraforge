package io.infraforge.auth;

import io.infraforge.config.InfraforgeProperties;
import io.infraforge.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates HS256 JWTs for the infraforge API.
 *
 * <p>The signing key is loaded from {@code infraforge.jwt.secret}. In production
 * this value is injected from Secrets Manager via Spring's property resolution chain.
 * Rotate the key by updating the secret and redeploying — old tokens will be
 * rejected immediately.</p>
 *
 * <p>Upgrade path to RS256: replace {@link SecretKey} with an {@code RSAKey} pair
 * loaded from Secrets Manager, and update {@code signWith}/{@code verifyWith} calls.
 * The rest of this class is unchanged.</p>
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL  = "email";
    private static final String CLAIM_LOGIN  = "login";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(InfraforgeProperties props) {
        // Key must be at least 256 bits for HS256.
        byte[] keyBytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        this.signingKey  = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = props.jwt().expirationMs();
    }

    /** Expiration in milliseconds — used by callers that need to set cookie max-age. */
    public long expirationMs() { return expirationMs; }

    /**
     * Issue a signed JWT for the given user.
     *
     * @return compact JWT string
     */
    public String issue(User user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.userId())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_LOGIN, user.login())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate a JWT and return the principal, or empty if invalid/expired.
     */
    public Optional<User> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new User(
                    claims.getSubject(),
                    claims.get(CLAIM_LOGIN,  String.class),
                    claims.get(CLAIM_EMAIL,  String.class)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
