package hu.riskguard.core.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Getter
public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User delegate;
    private final UUID userId;
    private final UUID tenantId;

    public CustomOAuth2User(OAuth2User delegate, UUID userId, UUID tenantId) {
        this.delegate = delegate;
        this.userId = userId;
        this.tenantId = tenantId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}