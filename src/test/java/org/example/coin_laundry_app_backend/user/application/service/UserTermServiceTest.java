package org.example.coin_laundry_app_backend.user.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.Term;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.TermAgree;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.User;
import org.example.coin_laundry_app_backend.user.domain.model.enums.TermType;
import org.example.coin_laundry_app_backend.user.domain.model.value.PhoneNumber;
import org.example.coin_laundry_app_backend.user.domain.model.value.TermInfo;
import org.example.coin_laundry_app_backend.user.domain.service.TermAgreeService;
import org.example.coin_laundry_app_backend.user.domain.service.TermService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserTermService는")
class UserTermServiceTest {

    @InjectMocks
    private UserTermService userTermService;

    @Mock
    private UserService userService;
    @Mock
    private TermService termService;
    @Mock
    private TermAgreeService termAgreeService;

    private final Long expectedUserId = 1L;
    private final Long expectedTermId = 1L;
    private final LocalDateTime currentTime = LocalDateTime.now();
    private final PhoneNumber expectedPhoneNumber = PhoneNumber.from("010-1234-5678");
    private final User expectedUser = spy(User.from(expectedPhoneNumber));
    private final Term expectedTerm = spy(
        Term.of(TermType.MANDATORY, TermInfo.of("testTitle", 1), "testContext", currentTime));
    private final TermAgree expectedTermAgree = spy(
        TermAgree.of(expectedUser, expectedTerm, true, currentTime));

    @BeforeEach
    void init() {
        given(expectedUser.getId()).willReturn(expectedUserId);
        given(expectedTerm.getId()).willReturn(expectedTermId);
    }

    @Nested
    @DisplayName("동의 약관을 추가할 때")
    class whenAgreeTerm {

        @BeforeEach
        void init() {
            given(userService.getUserById(expectedUserId)).willReturn(Mono.just(expectedUser));
        }

        @Test
        @DisplayName("동의 약관이 존재하지 않으면 새로운 동의 약관을 생성한다.")
        void shouldCreateNewTermAgree() {
            // Arrange
            given(termAgreeService.getTermAgreeByUserIdAndTermId(expectedUserId,
                expectedTermId)).willReturn(Mono.empty());
            given(termAgreeService.addTermAgree(any())).willReturn(Mono.just(expectedTermAgree));
            given(termService.getTermById(expectedTermId)).willReturn(Mono.just(expectedTerm));
            // Act & Assert
            StepVerifier.create(userTermService.agreeTerm(expectedUserId, expectedTermId))
                .expectNextMatches(
                    response -> response.getUserId().equals(expectedUserId) && response.getTermId()
                        .equals(expectedTermId))
                .verifyComplete();
            verify(termAgreeService).addTermAgree(any());
        }

        @Nested
        @DisplayName("동의 약관이 이미 존재할 때")
        class whenTermAgreeExist {

            @Test
            @DisplayName("이미 동의한 약관이면 에러를 반환한다.")
            void shouldReturnErrorWhenAlreadyAgree() {
                // Arrange
                given(termAgreeService.getTermAgreeByUserIdAndTermId(expectedUserId,
                    expectedTermId)).willReturn(Mono.just(expectedTermAgree));

                // Act & Assert
                StepVerifier.create(userTermService.agreeTerm(expectedUserId, expectedTermId))
                    .expectError(IllegalArgumentException.class)
                    .verify();
            }

            @Test
            @DisplayName("이미 동의한 약관이 아니면 동의 약관을 업데이트한다.")
            void shouldUpdateTermAgreeWhenNotAgree() {
                // Arrange
                given(termAgreeService.getTermAgreeByUserIdAndTermId(expectedUserId,
                    expectedTermId)).willReturn(Mono.just(expectedTermAgree));
                given(expectedTermAgree.getAgreeYn()).willReturn(false);
                given(termAgreeService.updateTermAgree(expectedTermAgree)).willReturn(
                    Mono.just(expectedTermAgree));

                // Act & Assert
                StepVerifier.create(userTermService.agreeTerm(expectedUserId, expectedTermId))
                    .expectNextMatches(
                        response -> response.getUserId().equals(expectedUserId)
                            && response.getTermId()
                            .equals(expectedTermId))
                    .verifyComplete();
                verify(termAgreeService).updateTermAgree(expectedTermAgree);
            }

        }
    }

    @Nested
    @DisplayName("동의 약관을 철회할 때")
    class whenDisagreeTerm {

        @Test
        @DisplayName("동의 약관이 존재하지 않으면 에러를 반환한다.")
        void shouldReturnErrorWhenTermAgreeNotExist() {
            // Arrange
            given(termAgreeService.getTermAgreeByUserIdAndTermId(expectedUserId,
                expectedTermId)).willReturn(Mono.empty());

            // Act & Assert
            StepVerifier.create(userTermService.disagreeTerm(expectedUserId, expectedTermId))
                .expectError(IllegalArgumentException.class)
                .verify();
        }

        @Test
        @DisplayName("동의 약관이 이미 철회한 상태이면 에러를 반환한다.")
        void shouldReturnErrorWhenAlreadyDisagree() {
            // Arrange
            given(termAgreeService.getTermAgreeByUserIdAndTermId(expectedUserId,
                expectedTermId)).willReturn(Mono.just(expectedTermAgree));
            given(expectedTermAgree.getAgreeYn()).willReturn(false);

            // Act & Assert
            StepVerifier.create(userTermService.disagreeTerm(expectedUserId, expectedTermId))
                .expectError(IllegalArgumentException.class)
                .verify();
        }

        @Test
        @DisplayName("동의 약관을 철회한다.")
        void shouldDisagreeTerm() {
            // Arrange
            given(termAgreeService.getTermAgreeByUserIdAndTermId(expectedUserId,
                expectedTermId)).willReturn(Mono.just(expectedTermAgree));
            given(termAgreeService.updateTermAgree(expectedTermAgree)).willReturn(
                Mono.just(expectedTermAgree));
            given(expectedTermAgree.getAgreeYn()).willReturn(true);

            // Act & Assert
            StepVerifier.create(userTermService.disagreeTerm(expectedUserId, expectedTermId))
                .expectNextMatches(
                    response -> response.getUserId().equals(expectedUserId) && response.getTermId()
                        .equals(expectedTermId))
                .verifyComplete();
            verify(termAgreeService).updateTermAgree(expectedTermAgree);
        }

    }

    @Test
    @DisplayName("사용자의 동의 약관을 조회한다.")
    void shouldGetTermAgrees() {
        // Arrange
        given(termAgreeService.getTermAgreesByUserId(expectedUserId)).willReturn(
            Flux.just(expectedTermAgree));
        // Act & Assert
        StepVerifier.create(userTermService.getTermAgrees(expectedUserId))
            .expectNextMatches(
                response -> response.getUserId().equals(expectedUserId) && response.getTermId()
                    .equals(expectedTermId))
            .verifyComplete();
    }

}