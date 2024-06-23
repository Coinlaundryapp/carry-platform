package org.example.coin_laundry_app_backend.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.example.coin_laundry_app_backend.user.domain.model.entity.data.VerificationCodeData;
import org.example.coin_laundry_app_backend.user.domain.model.value.PhoneNumber;
import org.example.coin_laundry_app_backend.user.domain.service.VerificationCodeGenerator;
import org.example.coin_laundry_app_backend.user.repository.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UserSignServiceTest {

    private final Long expectedId = 1L;
    private final PhoneNumber expectedPhoneNumber = PhoneNumber.from("010-1234-5678");
    @InjectMocks
    private UserSignService userSignService;
    @Mock
    private VerificationCodeRepository verificationCodeRepository;
    private String expectedVerificationCode;
    private LocalDateTime expectedCreatedAt;
    private LocalDateTime expectedExpiredAt;
    private VerificationCodeData verificationCodeData;

    @BeforeEach
    void setUp() {
        expectedVerificationCode = VerificationCodeGenerator.generateVerificationCode();
        expectedCreatedAt = LocalDateTime.now();
        expectedExpiredAt = expectedCreatedAt.plusMinutes(3);
        verificationCodeData = new VerificationCodeData(expectedId,
            expectedPhoneNumber.getValue(),
            expectedVerificationCode,
            expectedCreatedAt,
            expectedExpiredAt);
    }

    @Test
    void 인증번호_생성_요청() {
        // Arrange
        when(verificationCodeRepository.save(any(VerificationCodeData.class))).thenReturn(
            Mono.just(verificationCodeData));
        // Act
        var verificationCode = userSignService.requestPhoneVerificationCode(expectedPhoneNumber)
            .block();
        // Assert
        assertThat(verificationCode).isNotNull()
            .hasFieldOrPropertyWithValue("id", expectedId)
            .hasFieldOrPropertyWithValue("phoneNumber", expectedPhoneNumber.getValue())
            .hasFieldOrPropertyWithValue("code", expectedVerificationCode)
            .hasFieldOrPropertyWithValue("createdAt", expectedCreatedAt)
            .hasFieldOrPropertyWithValue("expiredAt", expectedExpiredAt);
    }

    @Test
    void 인증번호_검증_성공() {
        // Arrange
        when(verificationCodeRepository.findByPhoneNumberAndCode(expectedPhoneNumber.getValue(),
            expectedVerificationCode)).thenReturn(Mono.just(verificationCodeData));
        // Act
        var actualResult = userSignService.verifyPhoneVerificationCode(expectedPhoneNumber,
            expectedVerificationCode).block();
        // Assert
        assertThat(actualResult).isNotNull()
            .hasFieldOrPropertyWithValue("id", expectedId)
            .hasFieldOrPropertyWithValue("phoneNumber", expectedPhoneNumber.getValue())
            .hasFieldOrPropertyWithValue("code", expectedVerificationCode)
            .hasFieldOrPropertyWithValue("createdAt", expectedCreatedAt)
            .hasFieldOrPropertyWithValue("expiredAt", expectedExpiredAt);
    }

    @Test
    void 인증번호_검증_실패_인증번호가_존재하지_않을_때() {
        // Arrange
        when(verificationCodeRepository.findByPhoneNumberAndCode(expectedPhoneNumber.getValue(),
            expectedVerificationCode)).thenReturn(Mono.empty());
        // Act & Assert
        var actualResult = userSignService.verifyPhoneVerificationCode(expectedPhoneNumber,
            expectedVerificationCode);
        assertThatThrownBy(actualResult::block)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid verification code");
    }

    @Test
    void 인증번호_검증_실패_만료된_인증번호() {
        // Arrange
        var expiredVerificationCodeData = new VerificationCodeData(expectedId,
            expectedPhoneNumber.getValue(),
            expectedVerificationCode,
            expectedCreatedAt.minusMinutes(10),
            expectedExpiredAt.minusMinutes(5));
        when(verificationCodeRepository.findByPhoneNumberAndCode(expectedPhoneNumber.getValue(),
            expectedVerificationCode)).thenReturn(Mono.just(expiredVerificationCodeData));
        // Act & Assert
        var actualResult = userSignService.verifyPhoneVerificationCode(expectedPhoneNumber,
            expectedVerificationCode);
        assertThatThrownBy(actualResult::block)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Expired verification code");
    }
}