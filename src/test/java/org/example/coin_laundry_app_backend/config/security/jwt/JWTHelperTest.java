package org.example.coin_laundry_app_backend.config.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.exceptions.JWTVerificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("기능 테스트: JWTHelper")
class JWTHelperTest {

    private static final JWTHelper jwtHelper;
    private static final String ISSUER = "CoinLaundryApp";
    private static final String CLIENT_SECRET = "coin_laundry_app";

    static {
        var jwtProperties = new JWTProperties();
        jwtProperties.setIssuer("CoinLaundryApp");
        jwtProperties.setClientSecret("coin_laundry_app");
        jwtProperties.setAccessTokenExpiryDate(1L);
        jwtHelper = new JWTHelper(jwtProperties);
    }

    @Test
    void 토큰_발급() {
        // Arrange
        long expectedUserId = 1L;
        // Act
        var actualResult = jwtHelper.sign(expectedUserId);
        // Assert
        assertThat(actualResult).isNotNull();
    }

    @Test
    void 토큰_검증_성공() {
        // Arrange
        long expectedUserId = 1L;
        var token = jwtHelper.sign(expectedUserId);
        // Act
        var actualResult = jwtHelper.verify(token);
        // Assert
        assertThat(actualResult).isEqualTo(expectedUserId);
    }

    @Test
    void 토큰_검증_실패_잘못된_토큰형식() {
        // Arrange
        var token = "invalid_token";
        // Act & Assert
        assertThatThrownBy(() -> jwtHelper.verify(token))
            .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void 토큰_검증_실패_만료된_토큰() {
        // Arrange
        var corruptJwtHelper = new JWTHelper(new JWTProperties() {
            @Override
            public String getIssuer() {
                return ISSUER;
            }

            @Override
            public String getClientSecret() {
                return CLIENT_SECRET;
            }

            @Override
            public Long getAccessTokenExpiryDate() {
                return 0L;
            }
        });
        var token = corruptJwtHelper.sign(1L);
        // Act & Assert
        assertThatThrownBy(() -> jwtHelper.verify(token))
            .isInstanceOf(JWTVerificationException.class);
    }
}