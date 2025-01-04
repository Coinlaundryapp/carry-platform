package com.carry_laundry.carry_backend.config.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JWTHelper는")
class JWTHelperTest {

    private static final long USER_ID = 1L;
    private static final Map<String, Long> ACCEPTED_TERMS = Map.of(
        "TERM_AGREE", 1L,
        "PRIVACY_AGREE", 4L,
        "LOCATION_AGREE", 3L
    );

    private static final JWTProperties JWT_PROPERTIES = new JWTProperties();

    static {
        JWT_PROPERTIES.setIssuer("ISSUER_TEST");
        JWT_PROPERTIES.setClientSecret("CLIENT_SECRET");
        JWT_PROPERTIES.setAccessTokenExpiryDate(1L);
        JWT_PROPERTIES.setRefreshTokenExpiryDate(1L);
    }

    private static final JWTHelper JWT_HELPER = new JWTHelper(JWT_PROPERTIES);

    @Nested
    @DisplayName("토큰을 생성할 때")
    class WhenCreateToken {

        @Test
        @DisplayName("AccessToken, RefreshToken을 정상적으로 생성한다.")
        void WillSignAccessTokenAndRefreshTokenSuccess() {
            // Act
            var actualResult = JWT_HELPER.sign(USER_ID, ACCEPTED_TERMS);
            // Assert
            assertThat(actualResult)
                .isNotNull()
                .hasFieldOrProperty("accessToken")
                .hasFieldOrProperty("refreshToken")
                .hasFieldOrProperty("refreshTokenExpiryAt");
        }

        @Test
        @DisplayName("AccessToken을 정상적으로 생성한다.")
        void WillSignAccessTokenSuccess() {
            // Arrange
            String expectedRefreshToken = "REFRESH_TOKEN_TEST";
            LocalDateTime expectedRefreshTokenExpiryDate = LocalDateTime.now();
            // Act
            var actualResult = JWT_HELPER.sign(USER_ID, ACCEPTED_TERMS, expectedRefreshToken,
                expectedRefreshTokenExpiryDate);
            // Assert
            assertThat(actualResult)
                .isNotNull()
                .hasFieldOrPropertyWithValue("refreshToken", expectedRefreshToken)
                .hasFieldOrPropertyWithValue("refreshTokenExpiryAt",
                    expectedRefreshTokenExpiryDate);
        }


    }

    @Nested
    @DisplayName("토큰을 파싱할 때")
    class WhenParseToken {

        @Test
        @DisplayName("정상적으로 파싱한다.")
        void WillParseTokenSuccess() {
            // Arrange
            String accessToken = JWT_HELPER.sign(USER_ID, ACCEPTED_TERMS).getAccessToken();
            // Act
            var actualResult = JWT_HELPER.parse(accessToken);
            // Assert
            assertThat(actualResult)
                .isNotNull()
                .hasFieldOrPropertyWithValue("userId", USER_ID)
                .hasFieldOrPropertyWithValue("acceptedTerms", ACCEPTED_TERMS);
        }

        @Test
        @DisplayName("만료된 토큰을 파싱하면 JWTVerificationException을 던진다.")
        void WillThrowJWTVerificationExceptionWhenParseExpiredToken() {
            // Arrange
            JWTProperties jwtProperties = new JWTProperties();
            jwtProperties.setIssuer("ISSUER_TEST");
            jwtProperties.setClientSecret("CLIENT_SECRET");
            jwtProperties.setAccessTokenExpiryDate(0L);
            jwtProperties.setRefreshTokenExpiryDate(1L);
            JWTHelper jwtHelper = new JWTHelper(jwtProperties);
            String accessToken = jwtHelper.sign(USER_ID, ACCEPTED_TERMS).getAccessToken();
            // Act & Assert
            assertThatThrownBy(() -> jwtHelper.parse(accessToken))
                .isInstanceOf(JWTVerificationException.class)
                .hasMessageContaining("Token has expired");
        }

        @Test
        @DisplayName("잘못된 토큰을 파싱하면 JWTVerificationException을 던진다.")
        void WillThrowJWTVerificationExceptionWhenParseInvalidToken() {
            // Arrange
            String invalidToken = "INVALID_TOKEN";
            // Act & Assert
            assertThatThrownBy(() -> JWT_HELPER.parse(invalidToken))
                .isInstanceOf(JWTVerificationException.class)
                .hasMessageContaining(
                    "The token was expected to have 3 parts, but got 0.");
        }

    }
}