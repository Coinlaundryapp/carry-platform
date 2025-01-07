package com.carry_laundry.carry_backend.common.security.properties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "jwt")
@NoArgsConstructor
public class JWTProperties {

    private String issuer;
    private String clientSecret;
    private Long accessTokenExpiryDate;
    private Long refreshTokenExpiryDate;
}
