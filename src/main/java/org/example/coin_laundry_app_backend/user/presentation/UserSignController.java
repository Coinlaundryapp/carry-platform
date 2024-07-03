package org.example.coin_laundry_app_backend.user.presentation;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.notification.application.service.NotificationService;
import org.example.coin_laundry_app_backend.notification.domain.model.value.NotificationMessage;
import org.example.coin_laundry_app_backend.user.application.service.UserSignService;
import org.example.coin_laundry_app_backend.user.domain.model.value.PhoneNumber;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.SignUpRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.VerificationCodeRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.VerifyCodeRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.LoginResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/sign")
@RequiredArgsConstructor
public class UserSignController {

    private final UserSignService userSignService;
    private final NotificationService notificationService;

    @PostMapping("/request-code")
    public Mono<ApiCommonResponse<Void>> requestVerificationCode(
        @RequestBody VerificationCodeRequest request) {
        var phoneNumber = PhoneNumber.from(request.getPhoneNumber());
        return userSignService.requestPhoneVerificationCode(phoneNumber).flatMap(
            vc -> notificationService.sendToIndividual(
                new NotificationMessage("AUTH-001", phoneNumber.toString(),
                    new String[]{vc.getCode()}))
        ).then(Mono.just(ApiCommonResponse.createSuccessResponse()));
    }

    @PostMapping("/verify")
    public Mono<ApiCommonResponse<Void>> verifyVerificationCode(
        @RequestBody VerifyCodeRequest request) {
        return userSignService.verifyPhoneVerificationCode(
                PhoneNumber.from(request.getPhoneNumber()), request.getVerificationCode())
            .then(Mono.just(ApiCommonResponse.createSuccessResponse()));
    }
    
    @PostMapping("/login")
    public Mono<ApiCommonResponse<LoginResponse>> login(@RequestBody VerifyCodeRequest request) {
        return userSignService.login(PhoneNumber.from(request.getPhoneNumber()),
            request.getVerificationCode()).map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/sign-up")
    public Mono<ApiCommonResponse<Void>> signUp(@RequestBody SignUpRequest request) {
        return userSignService.signUp(PhoneNumber.from(request.getPhoneNumber()),
                request.getCommercialYn(), request.getLocationYn())
            .then(Mono.just(ApiCommonResponse.createSuccessResponse()));
    }

}
