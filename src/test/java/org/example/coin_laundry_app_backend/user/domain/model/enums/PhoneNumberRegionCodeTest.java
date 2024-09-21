package org.example.coin_laundry_app_backend.user.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RegionCode는")
class PhoneNumberRegionCodeTest {

    @Nested
    @DisplayName("from 메소드로 조회할 때")
    class whenUsingForm {

        @Test
        @DisplayName("국가 코드가 존재하면 해당 국가 코드를 반환한다.")
        void shouldReturnRegionCodeWhenExists() {
            // Arrange
            String expectedRegionCode = "+82";
            // Act
            PhoneNumberRegionCode actualResult = PhoneNumberRegionCode.from(expectedRegionCode);
            // Assert
            assertThat(actualResult)
                .isEqualTo(PhoneNumberRegionCode.SOUTH_KOREA);
        }

        @Test
        @DisplayName("지원하지 않는 국가 코드의 경우 예외를 던진다.")
        void shouldThrowExceptionWhenRegionCodeNotExists() {
            // Arrange
            String expectedRegionCode = "+81";
            // Act & Assert
            assertThatThrownBy(() -> PhoneNumberRegionCode.from(expectedRegionCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 국가 코드입니다.");
        }

    }

    @Test
    @DisplayName("RegionCode의 값을 반환한다.")
    void shouldReturnRegionCodeValue() {
        // Arrange
        String expectedRegionCodeValue = "+82";
        // Act
        String actualResult = PhoneNumberRegionCode.SOUTH_KOREA.getValue();
        // Assert
        assertThat(actualResult)
            .isEqualTo(expectedRegionCodeValue);
    }

}