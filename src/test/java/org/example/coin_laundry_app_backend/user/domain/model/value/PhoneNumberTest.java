package org.example.coin_laundry_app_backend.user.domain.model.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.coin_laundry_app_backend.user.domain.model.enums.PhoneNumberRegionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PhoneNumber는")
class PhoneNumberTest {

    private final String expectedPhoneNumber = "+82 10-1234-5678";

    @Nested
    @DisplayName("from 메소드로 생성할 때")
    class whenUsingFrom {

        @Test
        @DisplayName("전화번호가 존재하면 해당 전화번호를 반환한다.")
        void shouldReturnPhoneNumberWhenExists() {
            // Act
            PhoneNumber actualResult = PhoneNumber.from(expectedPhoneNumber);
            // Assert
            assertThat(actualResult)
                .hasFieldOrPropertyWithValue("regionCode", PhoneNumberRegionCode.SOUTH_KOREA)
                .hasFieldOrPropertyWithValue("value", "10-1234-5678");
        }

        @Test
        @DisplayName("잘못된 전화번호 형식의 경우 예외를 던진다.")
        void shouldThrowExceptionWhenPhoneNumberNotExists() {
            // Arrange
            String wrongPhoneNumber = "+82 10-1234-567";
            // Act & Assert
            assertThatThrownBy(() -> PhoneNumber.from(wrongPhoneNumber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("잘못된 전화번호 형식입니다.");
        }

    }

    @Test
    @DisplayName("toString 메소드로 전화번호를 반환한다.")
    void shouldReturnValue() {
        // Arrange
        PhoneNumber phoneNumber = PhoneNumber.from(expectedPhoneNumber);
        // Act
        String actualResult = phoneNumber.toString();
        // Assert
        assertThat(actualResult)
            .isEqualTo(expectedPhoneNumber);
    }

}