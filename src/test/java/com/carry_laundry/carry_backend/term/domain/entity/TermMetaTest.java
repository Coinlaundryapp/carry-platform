package com.carry_laundry.carry_backend.term.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carry_laundry.carry_backend.term.domain.enums.TermType;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("TermMeta 엔티티는")
class TermMetaTest {

    private static final String TITLE = "이용약관";
    private static final String CODE = "PRIVATE_INFO";
    private static final TermType TERM_TYPE = TermType.MANDATORY;


    @Nested
    @DisplayName("생성 시")
    class WhenCreated {

        @Test
        @DisplayName("정상적으로 생성된다.")
        void WillBeCreated() {
            // Act
            TermMeta actualResult = TermMeta.of(TITLE, CODE, TERM_TYPE);
            // Assert
            assertThat(actualResult).isNotNull()
                .extracting("title", "code", "termType")
                .containsExactly(TITLE, CODE, TERM_TYPE);
        }

        @MethodSource("WillThrowExceptionIfValueIsNullOrBlank")
        @ParameterizedTest
        @DisplayName("title이 null이거나 빈 문자열이면 IllegalArgumentException이 발생한다.")
        void WillThrowExceptionIfTitleIsNullOrBlank(String expectedTitle) {
            // Act & Assert
            assertThatThrownBy(() -> TermMeta.of(expectedTitle, CODE, TERM_TYPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title should not be blank");
        }

        @MethodSource("WillThrowExceptionIfValueIsNullOrBlank")
        @ParameterizedTest
        @DisplayName("code가 null이거나 빈 문자열이면 IllegalArgumentException이 발생한다.")
        void WillThrowExceptionIfCodeIsNullOrBlank(String expectedCode) {
            // Act & Assert
            assertThatThrownBy(() -> TermMeta.of(TITLE, expectedCode, TERM_TYPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code should not be blank");
        }

        @Test
        @DisplayName("termType이 null이면 IllegalArgumentException이 발생한다.")
        void WillThrowExceptionIfTermTypeIsNull() {
            // Act & Assert
            assertThatThrownBy(() -> TermMeta.of(TITLE, CODE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("termType should not be null");
        }

        private static Stream<Arguments> WillThrowExceptionIfValueIsNullOrBlank() {
            return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of(" "),
                Arguments.of("\t"),
                Arguments.of("\n")
            );
        }
    }

    @Nested
    @DisplayName("수정 시")
    class WhenUpdated {

        private TermMeta termMeta;
        private static final String EXPECTED_TITLE = "개인정보처리방침";
        private static final String EXPECTED_CODE = "USER_PRIVACY";
        private static final TermType EXPECTED_TERM_TYPE = TermType.OPTIONAL;

        @Test
        @DisplayName("정상적으로 수정된다.")
        void WillBeUpdated() {
            // Arrange
            termMeta = TermMeta.of(TITLE, CODE, TERM_TYPE);
            // Act
            termMeta.updateValues(EXPECTED_TITLE, EXPECTED_CODE, EXPECTED_TERM_TYPE);
            // Assert
            assertThat(termMeta).isNotNull()
                .extracting("title", "code", "termType")
                .containsExactly(EXPECTED_TITLE, EXPECTED_CODE, EXPECTED_TERM_TYPE);
        }

        @Test
        @DisplayName("title, code, termType이 모두 null이거나 빈 문자열이면 IllegalArgumentException이 발생한다.")
        void WillThrowExceptionIfAllValuesAreNullOrBlank() {
            // Arrange
            termMeta = TermMeta.of(TITLE, CODE, TERM_TYPE);
            // Act & Assert
            assertThatThrownBy(() -> termMeta.updateValues(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one parameter should be not null");
        }

        @ParameterizedTest
        @MethodSource
        @DisplayName("특정 값이 null이거나 빈 문자열이면 해당 값은 수정되지 않는다.")
        void WillNotUpdateIfValueIsNullOrBlank(String expectedTitle, String expectedCode,
            TermType expectedTermType) {
            // Arrange
            termMeta = TermMeta.of(TITLE, CODE, TERM_TYPE);
            // Act
            termMeta.updateValues(expectedTitle, expectedCode, expectedTermType);
            // Assert
            assertThat(termMeta).isNotNull()
                .extracting("title", "code", "termType")
                .containsExactly(
                    expectedTitle == null || expectedTitle.isBlank() ? TITLE : expectedTitle,
                    expectedCode == null || expectedCode.isBlank() ? CODE : expectedCode,
                    expectedTermType == null ? TERM_TYPE : expectedTermType);
        }

        private static Stream<Arguments> WillNotUpdateIfValueIsNullOrBlank() {
            return Stream.of(
                Arguments.of(null, EXPECTED_CODE, EXPECTED_TERM_TYPE),
                Arguments.of(EXPECTED_TITLE, null, EXPECTED_TERM_TYPE),
                Arguments.of(EXPECTED_TITLE, EXPECTED_CODE, null),
                Arguments.of("", EXPECTED_CODE, EXPECTED_TERM_TYPE),
                Arguments.of(EXPECTED_TITLE, "", EXPECTED_TERM_TYPE),
                Arguments.of(EXPECTED_TITLE, EXPECTED_CODE, TermType.MANDATORY),
                Arguments.of(" ", EXPECTED_CODE, EXPECTED_TERM_TYPE),
                Arguments.of(EXPECTED_TITLE, " ", EXPECTED_TERM_TYPE),
                Arguments.of("\t", EXPECTED_CODE, EXPECTED_TERM_TYPE),
                Arguments.of(EXPECTED_TITLE, "\t", EXPECTED_TERM_TYPE),
                Arguments.of("\n", EXPECTED_CODE, EXPECTED_TERM_TYPE)
            );
        }

    }
}