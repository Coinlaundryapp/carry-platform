package com.carry_laundry.carry_backend.config.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.carry_laundry.carry_backend.user.application.service.TermAdminService;
import com.carry_laundry.carry_backend.user.domain.model.entity.domainmodel.Term;
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
    @Mock
    private TermAdminService termAdminService;

    @BeforeEach
    void setUp() {
        given(jwtProperties.getIssuer()).willReturn("issuer");
        given(jwtProperties.getClientSecret()).willReturn("clientSecret");
        given(jwtProperties.getAccessTokenExpiryDate()).willReturn(1L);
        given(jwtProperties.getRefreshTokenExpiryDate()).willReturn(14L);
        jwtHelper = new JWTHelper(jwtProperties, termAdminService);
    }

    @Test
    @DisplayName("생성된다.")
    void create() {
        // Act & Assert
        assertThat(jwtHelper)
            .extracting("issuer", "termAdminService")
            .containsExactly("issuer", termAdminService);
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

    @Nested
    @DisplayName("토큰을 검증할 때")
    class whenVerify {

        private final Term expectedTerm = mock(Term.class);
        private String expectedAccessToken;

        @BeforeEach
        void init() {
            Long expectedUserId = 1L;
            Long expectedTermId = 1L;
            List<Long> expectedAcceptedTerms = List.of(expectedTermId);
            given(termAdminService.getRequiredTerms()).willReturn(List.of(expectedTerm));
            JWTTokenResponse expectedTokenResponse = jwtHelper.sign(expectedUserId,
                expectedAcceptedTerms);
            expectedAccessToken = expectedTokenResponse.getAccessToken();
        }

        @Test
        @DisplayName("정상적으로 검증한다.")
        void verifySuccess() {
            // Arrange
            given(expectedTerm.getId()).willReturn(1L);
            given(termAdminService.getRequiredTerms()).willReturn(List.of(expectedTerm));
            // Act
            Long actualResult = jwtHelper.verify(expectedAccessToken);
            // Assert
            assertThat(actualResult).isEqualTo(1L);
        }

        @Test
        @DisplayName("약관 갱신이 필요한 경우 예외를 던진다.")
        void verifyFailWhenTermsNeedUpdate() {
            // Arrange
            given(expectedTerm.getId()).willReturn(2L);
            given(termAdminService.getRequiredTerms()).willReturn(List.of(expectedTerm));
            // Act & Assert
            assertThatThrownBy(() -> jwtHelper.verify(expectedAccessToken))
                .isInstanceOf(JWTVerificationException.class)
                .hasMessage("약관 동의가 필요합니다.");
        }
    }

}