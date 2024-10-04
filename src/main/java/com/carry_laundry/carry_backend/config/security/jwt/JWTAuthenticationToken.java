package com.carry_laundry.carry_backend.config.security.jwt;

import java.util.Collection;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

@Getter
public class JWTAuthenticationToken extends AbstractAuthenticationToken {

    private Object principal;

    public JWTAuthenticationToken(Object principal,
        Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
