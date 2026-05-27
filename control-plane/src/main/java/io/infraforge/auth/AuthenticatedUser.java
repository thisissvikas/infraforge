package io.infraforge.auth;

import io.infraforge.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security {@link Authentication} implementation wrapping our {@link User} domain record.
 * Set on the {@link org.springframework.security.core.context.SecurityContext} by the auth filters.
 */
public final class AuthenticatedUser implements Authentication {

    private final User user;
    private boolean authenticated = true;

    public AuthenticatedUser(User user) {
        this.user = user;
    }

    public User user()      { return user; }
    public String userId()  { return user.userId(); }
    public String email()   { return user.email(); }
    public String login()   { return user.login(); }

    @Override public Object getPrincipal()                          { return user; }
    @Override public String getName()                               { return user.userId(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public Object getCredentials()                        { return null; }
    @Override public Object getDetails()                            { return null; }
    @Override public boolean isAuthenticated()                      { return authenticated; }
    @Override public void setAuthenticated(boolean b)               { this.authenticated = b; }
}
