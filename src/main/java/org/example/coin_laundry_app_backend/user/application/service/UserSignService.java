package org.example.coin_laundry_app_backend.user.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.VerificationCode;
import org.example.coin_laundry_app_backend.user.domain.model.value.PhoneNumber;
import org.example.coin_laundry_app_backend.user.domain.service.VerificationCodeGenerator;
import org.example.coin_laundry_app_backend.user.repository.VerificationCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@Transactional
@RequiredArgsConstructor
public class UserSignService {

    private final VerificationCodeRepository verificationCodeRepository;

    /**
     * Request a phone verification code.
     * Generates a verification code and saves it to the database.
     *
     * @param phoneNumber The phone number to which the verification code is sent.
     * @return A Mono of the VerificationCode.
     */
    public Mono<VerificationCode> requestPhoneVerificationCode(PhoneNumber phoneNumber) {
        // Generate verification code
        var verificationCode = new VerificationCode(phoneNumber.getValue(),
            VerificationCodeGenerator.generateVerificationCode());
        // Save verification code to database
        return verificationCodeRepository.save(verificationCode.toDataEntity())
            .map(VerificationCode::new);
    }


    /**
     * Verify the phone verification code.
     * Checks if the provided verification code matches the one in the database and is not expired.
     *
     * @param phoneNumber The phone number to which the verification code was sent.
     * @param verificationCode The verification code to verify.
     * @return A Mono of the VerificationCode. If the verification code is invalid or expired, an error is thrown.
     */
    // TODO: Is Necessary to delete verification code from database?
    public Mono<VerificationCode> verifyPhoneVerificationCode(PhoneNumber phoneNumber,
        String verificationCode) {
        var current = LocalDateTime.now();
        // Verify verification code via database
        // if Mono is Empty, throw exception else Map to VerificationCode
        return verificationCodeRepository.findByPhoneNumberAndCode(phoneNumber.getValue(),
                verificationCode)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid verification code")))
            .map(VerificationCode::new)
            .filter(vc -> !vc.isExpired(current))
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Expired verification code")));
    }
}
