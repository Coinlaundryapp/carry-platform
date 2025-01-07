package com.carry_laundry.carry_backend.common.security.payload;

import java.util.Collection;
import lombok.Getter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

@Getter
public class JWTAuthenticationToken extends AbstractAuthenticationToken {

    private final transient Object principal;

    public JWTAuthenticationToken(@NonNull Object principal,
        Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof JWTAuthenticationToken that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        return getPrincipal().equals(that.getPrincipal());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getPrincipal().hashCode();
        return result;
    }
}
