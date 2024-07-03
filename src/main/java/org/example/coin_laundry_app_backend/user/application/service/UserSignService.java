package org.example.coin_laundry_app_backend.user.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTHelper;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.User;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.VerificationCode;
import org.example.coin_laundry_app_backend.user.domain.model.value.PhoneNumber;
import org.example.coin_laundry_app_backend.user.domain.service.VerificationCodeGenerator;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.LoginResponse;
import org.example.coin_laundry_app_backend.user.repository.VerificationCodeRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserSignService {

    private final UserService userService;
    private final VerificationCodeRepository verificationCodeRepository;
    private final JWTHelper jwtHelper;

    /**
     * 전화번호 인증 코드를 요청합니다. 인증 코드를 생성하고 데이터베이스에 저장합니다.
     *
     * @param phoneNumber 인증 코드가 전송될 전화번호.
     * @return VerificationCode 의 Mono 객체.
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
     * 전화번호 인증 코드를 검증합니다. 데이터베이스에서 인증 코드를 확인합니다. Mono가 비어 있는 경우 예외가 발생합니다. 그렇지 않으면,
     * VerificationCode로 매핑됩니다.
     *
     * @param phoneNumber      전화번호
     * @param verificationCode 인증 코드
     * @return VerificationCode의 Mono 객체. 인증 코드가 유효하지 않거나 만료된 경우, 오류가 발생합니다.
     */
    // TODO: 데이터베이스에서 인증 코드를 삭제할 필요가 있는지 확인 필요
    public Mono<VerificationCode> verifyPhoneVerificationCode(PhoneNumber phoneNumber,
        String verificationCode) {
        // Verify verification code via database
        // if Mono is Empty, throw exception else Map to VerificationCode
        return verificationCodeRepository.findByPhoneNumberAndCode(phoneNumber.getValue(),
                verificationCode)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid verification code")))
            .flatMap(vc -> validateVerificationCode(new VerificationCode(vc)));
    }

    /**
     * 사용자를 로그인합니다. 전화번호 인증 코드를 검증하고 JWT 토큰을 반환합니다.
     *
     * @param phoneNumber      인증 코드가 전송된 전화번호.
     * @param verificationCode 검증할 인증 코드.
     * @return LoginResponse 의 Mono 객체. 인증 코드가 유효하지 않거나 만료된 경우, 오류가 발생합니다.
     */
    public Mono<LoginResponse> login(PhoneNumber phoneNumber, String verificationCode) {
        return verifyPhoneVerificationCode(phoneNumber, verificationCode)
            .flatMap(vc -> userService.getUserByPhoneNumber(phoneNumber)
                .flatMap(user -> {
                    var token = jwtHelper.sign(user.getId());
                    return Mono.just(new LoginResponse(token));
                })
            );
    }

    /**
     * 사용자를 등록합니다. 사용자 정보를 저장합니다.
     *
     * @param phoneNumber  전화번호
     * @param commercialYn 광고성 정보 수신 동의 여부
     * @param locationYn   위치 정보 수신 동의 여부
     * @return User 의 Mono 객체.
     */
    public Mono<User> signUp(PhoneNumber phoneNumber, Boolean commercialYn, Boolean locationYn) {
        var user = User.of(phoneNumber, commercialYn, locationYn);
        return userService.addUser(user);
    }

    /**
     * 인증 코드를 검증합니다. 인증 코드가 만료되었는지 확인합니다. 인증 코드가 만료된 경우, 오류가 발생합니다.
     *
     * @param verificationCode 검증할 인증 코드.
     * @return 인증 코드의 Mono 객체.
     */
    private Mono<VerificationCode> validateVerificationCode(VerificationCode verificationCode) {
        if (verificationCode.isExpired(LocalDateTime.now())) {
            return Mono.error(new IllegalArgumentException("Expired verification code"));
        }
        return Mono.just(verificationCode);
    }

}
