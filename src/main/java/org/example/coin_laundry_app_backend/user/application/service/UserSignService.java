package org.example.coin_laundry_app_backend.user.application.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTHelper;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTTokenResponse;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.RefreshToken;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.TermAgree;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.User;
import org.example.coin_laundry_app_backend.user.domain.model.value.TermInfo;
import org.example.coin_laundry_app_backend.user.domain.service.KakaoOAuthService;
import org.example.coin_laundry_app_backend.user.domain.service.RefreshTokenService;
import org.example.coin_laundry_app_backend.user.domain.service.TermAgreeService;
import org.example.coin_laundry_app_backend.user.domain.service.TermService;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoAuthenticationResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUserResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUserTermsResponse.ServiceTerm;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.LoginResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserSignService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final TermAgreeService termAgreeService;
    private final TermService termService;
    private final KakaoOAuthService kakaoOAuthService;
    private final JWTHelper jwtHelper;


    /*login -> manageUser Rename
    authorizationCode -> accessToken 획득 -> KakaoUserInfo 획득 -> User 조회 -> 분기 발생
    Case1: Carry 서비스에 등록된 사용자가 없다! (회원가입 Case)
    -> User 생성 -> KakaoTermAgree 조회 -> TermAgree 생성 -> JWTToken 발급 -> RefreshToken 발급 -> LoginResponse 반환
    Case2: Carry 서비스에 등록된 사용자가 있다! (로그인 Case)
    -> User 조회 -> JWTToken 발급 -> RefreshToken 발급 -> LoginResponse 반환
    */
    public Mono<LoginResponse> manageUser(String authenticationCode) {
        return kakaoOAuthService.getKakaoAccessToken(authenticationCode)
            .flatMap(this::processKakaoAuthentication);
    }

    private Mono<LoginResponse> processKakaoAuthentication(
        KakaoAuthenticationResponse kakaoAuthResponse) {
        return kakaoOAuthService.getKakaoUserInfo(kakaoAuthResponse.getAccessToken())
            .flatMap(kakaoUserResponse ->
                userService.getUserByKakaoId(kakaoUserResponse.getId())
                    .flatMap(this::handleExistingUser)
                    .switchIfEmpty(
                        handleNewUser(kakaoUserResponse, kakaoAuthResponse.getAccessToken()))
            );
    }

    private Mono<LoginResponse> handleExistingUser(User user) {
        return generateLoginResponse(user.getId());
    }

    private Mono<LoginResponse> handleNewUser(KakaoUserResponse kakaoUserResponse,
        String accessToken) {
        return userService.addUser(User.from(kakaoUserResponse))
            .flatMap(newUser -> processKakaoTerms(newUser, accessToken)
                .then(generateLoginResponse(newUser.getId())));
    }

    private Mono<Void> processKakaoTerms(User user, String accessToken) {
        return kakaoOAuthService.getUserAgreeTerms(accessToken)
            .flatMap(kakaoUserTermsResponse -> {
                List<Mono<TermAgree>> termAgreeMono = Arrays.stream(
                        kakaoUserTermsResponse.getServiceTerms())
                    .filter(term -> term.getRequired() && term.getAgreed())
                    .map(term -> addTermAgree(user.getId(), term))
                    .toList();
                return Mono.when(termAgreeMono);
            })
            .then();
    }

    private Mono<TermAgree> addTermAgree(Long userId, ServiceTerm serviceTerm) {
        return termService.getTermByTermInfo(TermInfo.from(serviceTerm.getTag()))
            .flatMap(term -> termAgreeService.addTermAgree(
                TermAgree.of(userId, term.getId(), true, LocalDateTime.now())
            ));
    }

    private Mono<LoginResponse> generateLoginResponse(Long userId) {
        return termAgreeService.getTermAgreesByUserId(userId)
            .map(TermAgree::getTermId)
            .collectList()
            .flatMap(acceptedTerms -> {
                JWTTokenResponse jwtTokenResponse = jwtHelper.sign(userId, acceptedTerms);
                LoginResponse response = LoginResponse.from(jwtTokenResponse);
                return refreshTokenService.addRefreshToken(
                        RefreshToken.of(userId, jwtTokenResponse))
                    .thenReturn(response);
            });
    }

    public Mono<LoginResponse> reissue(String refreshToken) {
        return refreshTokenService.findRefreshTokenByValue(refreshToken)
            .flatMap(refreshTokenData -> {
                Long userId = refreshTokenData.getUserId();
                Mono<List<Long>> acceptedTermIds = termAgreeService.getTermAgreesByUserId(
                        userId)
                    .map(TermAgree::getTermId).collectList();
                return acceptedTermIds.flatMap(acceptedTerms -> {
                    JWTTokenResponse tokenResponse = jwtHelper.sign(userId, acceptedTerms,
                        refreshTokenData.getValue(), refreshTokenData.getExpiryAt());
                    return Mono.defer(() -> Mono.just(LoginResponse.from(tokenResponse)));
                });
            });
    }

}
