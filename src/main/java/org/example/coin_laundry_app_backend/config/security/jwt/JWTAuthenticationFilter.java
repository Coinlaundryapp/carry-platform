package org.example.coin_laundry_app_backend.config.security.jwt;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JWTAuthenticationFilter extends OncePerRequestFilter {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String EXCEPTION = "exception";

    private final JWTHelper jwtHelper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION)).ifPresentOrElse(
            token -> {
                token = URLDecoder.decode(token, StandardCharsets.UTF_8);
                if (!token.contains(TOKEN_PREFIX)) {
                    request.setAttribute(EXCEPTION,
                        new JWTVerificationException("Invalid token type"));
                } else {
                    token = token.substring(7);
                    try {
                        var userId = jwtHelper.verify(token);
                        var authentication = new JWTAuthenticationToken(userId,
                            generateAuthority());
                        authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } catch (Exception e) {
                        request.setAttribute(EXCEPTION, e);
                    }
                }
            },
            () -> request.setAttribute(EXCEPTION,
                new JWTVerificationException("Invalid token type"))
        );
        filterChain.doFilter(request, response);

    }

    private List<GrantedAuthority> generateAuthority() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
