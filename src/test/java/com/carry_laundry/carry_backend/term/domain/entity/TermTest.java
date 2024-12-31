package com.carry_laundry.carry_backend.term.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Term 엔티티는")
class TermTest {

    private static final Long TERM_META_ID = 1L;
    private static final String CONTENT = "content";
    private static final Integer VERSION_COUNT = 1;
    private static final LocalDate CREATED_AT = LocalDate.of(2024, 12, 25);

    @Nested
    @DisplayName("생성할 때")
    class WhenCreated {

        @Test
        @DisplayName("정상적으로 생성된다.")
        void willCreated() {
            // Act
            Term actualResult = Term.of(TERM_META_ID, CONTENT, VERSION_COUNT, CREATED_AT);
            // Assert
            assertThat(actualResult).isNotNull()
                .extracting("termMetaId", "content", "versionCount", "createdAt")
                .containsExactly(TERM_META_ID, CONTENT, VERSION_COUNT, CREATED_AT);
        }

        @ParameterizedTest
        @MethodSource("blankStrings")
        @DisplayName("content가 null이거나 공백이면 예외가 발생한다.")
        void willThrowExceptionIfContentIsNullOrBlank(String expectedContent) {
            // Act & Assert
            assertThatThrownBy(
                () -> Term.of(TERM_META_ID, expectedContent, VERSION_COUNT, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content should not be blank");
        }

        @Test
        @DisplayName("versionCount가 1보다 작으면 예외가 발생한다.")
        void willThrowExceptionIfVersionCountIsLessThanOne() {
            // Act & Assert
            assertThatThrownBy(
                () -> Term.of(TERM_META_ID, CONTENT, 0, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("versionCount should be greater than 0");
        }

        @Test
        @DisplayName("createdAt이 null이면 예외가 발생한다.")
        void willThrowExceptionIfCreatedAtIsNull() {
            // Act & Assert
            assertThatThrownBy(
                () -> Term.of(TERM_META_ID, CONTENT, VERSION_COUNT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("createdAt should not be null");
        }

        private static Stream<Arguments> blankStrings() {
            return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of(" "),
                Arguments.of("  ")
            );
        }
    }

    @Test
    @DisplayName("getVersion 메서드는 생성일과 버전 카운트를 조합하여 반환한다.")
    void getVersion() {
        // Arrange
        Term term = Term.of(TERM_META_ID, CONTENT, VERSION_COUNT, CREATED_AT);
        // Act
        String actualResult = term.getVersion();
        // Assert
        assertThat(actualResult).isEqualTo(CREATED_AT + "_" + VERSION_COUNT);
    }

}