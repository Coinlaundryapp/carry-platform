package org.example.coin_laundry_app_backend.config.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.auth0.jwt.exceptions.JWTVerificationException;
import java.util.List;
import org.example.coin_laundry_app_backend.user.application.service.TermAdminService;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.Term;
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
            String actualResult = jwtHelper.sign(expectedUserId, expectedAcceptedTerms);
            // Assert
            assertThat(actualResult).isNotNull();
        }
    }

    @Nested
    @DisplayName("토큰을 검증할 때")
    class whenVerify {

        private String expectedToken;
        private final Term expectedTerm = mock(Term.class);

        @BeforeEach
        void init() {
            Long expectedUserId = 1L;
            Long expectedTermId = 1L;
            List<Long> expectedAcceptedTerms = List.of(expectedTermId);
            given(termAdminService.getRequiredTerms()).willReturn(List.of(expectedTerm));
            expectedToken = jwtHelper.sign(expectedUserId, expectedAcceptedTerms);
        }

        @Test
        @DisplayName("정상적으로 검증한다.")
        void verifySuccess() {
            // Arrange
            given(expectedTerm.getId()).willReturn(1L);
            given(termAdminService.getRequiredTerms()).willReturn(List.of(expectedTerm));
            // Act
            Long actualResult = jwtHelper.verify(expectedToken);
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
            assertThatThrownBy(() -> jwtHelper.verify(expectedToken))
                .isInstanceOf(JWTVerificationException.class)
                .hasMessage("약관 동의가 필요합니다.");
        }
    }

}