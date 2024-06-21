package org.example.coin_laundry_app_backend.user.domain.model.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.coin_laundry_app_backend.user.domain.model.enums.RegionCode;
import org.junit.jupiter.api.Test;

class PhoneNumberTest {

    @Test
    void 전화번호_생성() {
        // Arrange
        var expectedPhoneNumber = "010-1234-5678";
        // Act
        var phoneNumber = PhoneNumber.from(expectedPhoneNumber);
        // Assert
        assertThat(phoneNumber)
            .hasFieldOrPropertyWithValue("regionCode", RegionCode.SOUTH_KOREA)
            .hasFieldOrPropertyWithValue("value", "010-1234-5678");
    }

    @Test
    void 전화번호_생성_실패_잘못된_전화번호_형식() {
        // Arrange
        var wrongPhoneNumber = "010-1234-567";
        // Act & Assert
        assertThatThrownBy(() -> PhoneNumber.from(wrongPhoneNumber))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Wrong Format Phone Number");
    }

    @Test
    void 전화번호_문자열_변환() {
        // Arrange
        var expectedPhoneNumber = "010-1234-5678";
        var phoneNumber = PhoneNumber.from(expectedPhoneNumber);
        // Act
        var phoneNumberString = phoneNumber.toString();
        // Assert
        assertThat(phoneNumberString).isEqualTo(
            RegionCode.SOUTH_KOREA.getValue() + expectedPhoneNumber);
    }
}