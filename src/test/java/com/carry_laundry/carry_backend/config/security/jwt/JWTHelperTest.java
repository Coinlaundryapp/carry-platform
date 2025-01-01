package com.carry_laundry.carry_backend.config.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWTHelper는")
class JWTHelperTest {

    private JWTHelper jwtHelper;

    @Mock
    private JWTProperties jwtProperties;

    @BeforeEach
    void setUp() {
        given(jwtProperties.getIssuer()).willReturn("issuer");
        given(jwtProperties.getClientSecret()).willReturn("clientSecret");
        given(jwtProperties.getAccessTokenExpiryDate()).willReturn(1L);
        given(jwtProperties.getRefreshTokenExpiryDate()).willReturn(14L);
        jwtHelper = new JWTHelper(jwtProperties);
    }

    @Test
    @DisplayName("생성된다.")
    void create() {
        // Act & Assert
        assertThat(jwtHelper)
            .extracting("issuer")
            .isEqualTo("issuer");
    }

    @Nested
    @DisplayName("토큰을 생성할 때")
    class whenSign {

        @Test
        @DisplayName("정상적으로 생성한다.")
        void signSuccess() {
            // Arrange
            Long expectedUserId = 1L;
            Long expectedTermId = 1L;
            List<Long> expectedAcceptedTerms = List.of(expectedTermId);
            // Act
            JWTTokenResponse actualResult = jwtHelper.sign(expectedUserId, expectedAcceptedTerms);
            // Assert
            assertThat(actualResult).isNotNull();
        }
    }

}