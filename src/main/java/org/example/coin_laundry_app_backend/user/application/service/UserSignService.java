package org.example.coin_laundry_app_backend.user.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTHelper;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTTokenResponse;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.RefreshToken;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.TermAgree;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.User;
import org.example.coin_laundry_app_backend.user.domain.service.KakaoOAuthService;
import org.example.coin_laundry_app_backend.user.domain.service.RefreshTokenService;
import org.example.coin_laundry_app_backend.user.domain.service.TermAgreeService;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.LoginResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserSignService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final TermAgreeService termAgreeService;
    private final KakaoOAuthService kakaoOAuthService;
    private final JWTHelper jwtHelper;

    public Mono<LoginResponse> login(String accessToken) {
        return kakaoOAuthService.getKakaoUserInfo(accessToken).flatMap(kakaoUserResponse -> {
            Long kakaoId = kakaoUserResponse.getId();
            return userService.getUserByKakaoId(kakaoId).flatMap(user -> {
                Long userId = user.getId();
                return termAgreeService.getTermAgreesByUserId(userId).map(TermAgree::getTermId)
                    .collectList().flatMap(acceptedTerms -> {
                        JWTTokenResponse jwtTokenResponse = jwtHelper.sign(userId, acceptedTerms);
                        LoginResponse response = LoginResponse.from(jwtTokenResponse);
                        return refreshTokenService.addRefreshToken(
                            RefreshToken.of(userId, jwtTokenResponse)).thenReturn(response);
                    });
            }).switchIfEmpty(
                Mono.fromCallable(() -> Mono.error(new IllegalArgumentException("User not found")))
                    .cast(LoginResponse.class));
        });
    }


    public Mono<User> signUp(String kakaoAccessToken) {
        return kakaoOAuthService.getKakaoUserInfo(kakaoAccessToken)
            .flatMap(kakaoUserResponse -> userService.addUser(User.from(kakaoUserResponse)));
    }

    public Mono<LoginResponse> reissue(String refreshToken) {
        return refreshTokenService.findRefreshTokenByValue(refreshToken)
            .flatMap(refreshTokenData -> {
                Long userId = refreshTokenData.getUserId();
                Mono<List<Long>> acceptedTermIds = termAgreeService.getTermAgreesByUserId(userId)
                    .map(TermAgree::getTermId).collectList();
                return acceptedTermIds.flatMap(acceptedTerms -> {
                    JWTTokenResponse tokenResponse = jwtHelper.sign(userId, acceptedTerms,
                        refreshTokenData.getValue(), refreshTokenData.getExpiryAt());
                    return Mono.defer(() -> Mono.just(LoginResponse.from(tokenResponse)));
                });
            });
    }

}
