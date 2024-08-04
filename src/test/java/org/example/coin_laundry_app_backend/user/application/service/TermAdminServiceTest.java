package org.example.coin_laundry_app_backend.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.Term;
import org.example.coin_laundry_app_backend.user.domain.model.enums.TermType;
import org.example.coin_laundry_app_backend.user.domain.model.value.TermInfo;
import org.example.coin_laundry_app_backend.user.domain.service.TermService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("TermAdminService는")
class TermAdminServiceTest {

    @Mock
    private TermService termService;

    @Test
    @DisplayName("생성시 최신 필수 약관 정보를 조회한다.")
    void testCreation() {
        // Arrange
        String expectedTitle = "testTitle";
        LocalDateTime expectedCreatedAt = LocalDateTime.now();
        List<Term> expectedTerms = List.of(
            Term.of(TermType.MANDATORY, TermInfo.of(expectedTitle, 2), "testContext",
                expectedCreatedAt),
            Term.of(TermType.MANDATORY, TermInfo.of(expectedTitle + 1, 1), "testContext",
                expectedCreatedAt)
        );
        Flux<Term> terms = Flux.just(
            Term.of(TermType.MANDATORY, TermInfo.of(expectedTitle, 1), "testContext",
                expectedCreatedAt),
            expectedTerms.get(0),
            expectedTerms.get(1),
            Term.of(TermType.OPTIONAL, TermInfo.of(expectedTitle + 2, 1), "testContext",
                expectedCreatedAt)
        );
        given(termService.getAllTerms()).willReturn(terms);
        // Act
        TermAdminService actualResult = new TermAdminService(termService);
        // Assert
        assertThat(actualResult.getRequiredTerms())
            .contains(expectedTerms.get(0), expectedTerms.get(1));
    }

    @Nested
    @DisplayName("생성 후")
    class afterCreation {

        private TermAdminService termAdminService;

        @BeforeEach
        void setUp() {
            given(termService.getAllTerms()).willReturn(Flux.empty());
            termAdminService = new TermAdminService(termService);
        }

        @Nested
        @DisplayName("새로운 약관을 추가할 때")
        class whenCreateNewTerm {

            @Test
            @DisplayName("이미 존재하는 약관이면 예외를 던진다.")
            void testCreateNewTermWithAlreadyExist() {
                // Arrange
                Term term = Term.of(TermType.MANDATORY, TermInfo.of("testTitle", 1), "testContext",
                    LocalDateTime.now());
                given(termService.getTermsByTitle(term.getTermInfo().getTitle()))
                    .willReturn(Flux.just(term));
                // Act & Assert
                StepVerifier.create(termAdminService.createNewTerm(term))
                    .expectError(IllegalArgumentException.class)
                    .verify();
            }


            @Test
            @DisplayName("새로운 약관을 추가한다.")
            void testCreateNewTerm() {
                // Arrange
                TermType expectedTermType = TermType.MANDATORY;
                TermInfo expectedTermInfo = TermInfo.of("testTitle", 1);
                String expectedContext = "testContext";
                LocalDateTime expectedCreatedAt = LocalDateTime.now();
                Term term = Term.of(expectedTermType, expectedTermInfo, expectedContext,
                    expectedCreatedAt);
                given(termService.getTermsByTitle(any(String.class))).willReturn(Flux.empty());
                given(termService.addTerm(term)).willReturn(Mono.just(term));
                // Act & Assert
                StepVerifier.create(termAdminService.createNewTerm(term))
                    .assertNext(response -> assertThat(response)
                        .extracting(Term::getTermType, Term::getTermInfo, Term::getContext,
                            Term::getCreatedAt)
                        .contains(expectedTermType, expectedTermInfo, expectedContext,
                            expectedCreatedAt))
                    .verifyComplete();
            }

        }

        @Nested
        @DisplayName("기존 약관을 업데이트할 때")
        class whenUpdateTerm {

            private final Term expectedTerm = Term.of(TermType.MANDATORY,
                TermInfo.of("testTitle", 1), "testContext",
                LocalDateTime.now());

            @Test
            @DisplayName("업데이트할 약관이 존재하지 않으면 예외를 던진다.")
            void testUpdateTermWithNotExist() {
                // Arrange
                given(
                    termService.getTermsByTitle(expectedTerm.getTermInfo().getTitle())).willReturn(
                    Flux.empty());
                // Act & Assert
                StepVerifier.create(termAdminService.updateTerm(expectedTerm))
                    .expectError(IllegalArgumentException.class)
                    .verify();
            }

            @Test
            @DisplayName("이미 존재하는 약관 버전이 같거나 더 높으면 예외를 던진다.")
            void testUpdateTermWithAlreadyExist() {
                // Arrange
                Term existTerm = Term.of(TermType.MANDATORY, TermInfo.of("testTitle", 1),
                    "testContext",
                    LocalDateTime.now());
                given(termService.getTermsByTitle(expectedTerm.getTermInfo().getTitle()))
                    .willReturn(Flux.just(existTerm));
                // Act & Assert
                StepVerifier.create(termAdminService.updateTerm(expectedTerm))
                    .expectError(IllegalArgumentException.class)
                    .verify();
            }

            @Test
            @DisplayName("기존 약관을 업데이트한다.")
            void testUpdateTerm() {
                // Arrange
                Term existTerm = Term.of(TermType.MANDATORY, TermInfo.of("testTitle", 0),
                    "testContext",
                    LocalDateTime.now());
                given(termService.getTermsByTitle(expectedTerm.getTermInfo().getTitle()))
                    .willReturn(Flux.just(existTerm));
                given(termService.addTerm(expectedTerm)).willReturn(Mono.just(expectedTerm));
                // Act & Assert
                StepVerifier.create(termAdminService.updateTerm(expectedTerm))
                    .assertNext(response -> assertThat(response)
                        .extracting(Term::getTermType, Term::getTermInfo, Term::getContext,
                            Term::getCreatedAt)
                        .contains(expectedTerm.getTermType(), expectedTerm.getTermInfo(),
                            expectedTerm.getContext(), expectedTerm.getCreatedAt()))
                    .verifyComplete();
            }
        }

    }
}