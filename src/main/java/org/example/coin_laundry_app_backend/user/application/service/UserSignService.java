package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTHelper;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.TermAgree;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.User;
import org.example.coin_laundry_app_backend.user.domain.service.KakaoOAuthService;
import org.example.coin_laundry_app_backend.user.domain.service.TermAgreeService;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.LoginResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserSignService {

    private final UserService userService;
    private final TermAgreeService termAgreeService;
    private final KakaoOAuthService kakaoOAuthService;
    private final JWTHelper jwtHelper;

    public Mono<LoginResponse> login(String accessToken) {
        return kakaoOAuthService.getKakaoUserInfo(accessToken).flatMap(kakaoUserResponse -> {
            Long kakaoId = kakaoUserResponse.getId();
            return userService.getUserByKakaoId(kakaoId).flatMap(user -> {
                Long userId = user.getId();
                return termAgreeService.getTermAgreesByUserId(userId).map(TermAgree::getTermId)
                    .collectList().map(acceptedTerms -> {
                        String token = jwtHelper.sign(userId, acceptedTerms);
                        return new LoginResponse(token);
                    });
            }).switchIfEmpty(Mono.fromCallable(() -> new IllegalArgumentException("User not found"))
                .cast(LoginResponse.class));
        });
    }


    public Mono<User> signUp(String accessToken) {
        return kakaoOAuthService.getKakaoUserInfo(accessToken)
            .flatMap(kakaoUserResponse -> userService.addUser(User.from(kakaoUserResponse)));
    }


}
