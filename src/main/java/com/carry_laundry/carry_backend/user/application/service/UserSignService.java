package com.carry_laundry.carry_backend.user.application.service;

import com.carry_laundry.carry_backend.config.security.jwt.JWTHelper;
import com.carry_laundry.carry_backend.config.security.jwt.JWTTokenResponse;
import com.carry_laundry.carry_backend.term.application.service.TermAgreementService;
import com.carry_laundry.carry_backend.term.application.service.TermService;
import com.carry_laundry.carry_backend.term.domain.entity.TermAgreement;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthToken;
import com.carry_laundry.carry_backend.user.domain.entity.domainmodel.RefreshToken;
import com.carry_laundry.carry_backend.user.domain.entity.domainmodel.User;
import com.carry_laundry.carry_backend.user.presentation.payload.response.LoginResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserSignService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final TermService termService;
    private final TermAgreementService termAgreementService;
    private final KakaoOAuthService kakaoOAuthService;
    private final JWTHelper jwtHelper;


    /*login -> manageUser Rename
    authorizationCode -> accessToken 획득 -> KakaoUserInfo 획득 -> User 조회 -> 분기 발생
    Case1: Carry 서비스에 등록된 사용자가 없다! (회원가입 Case)
    -> User 생성 -> KakaoTermAgree 조회 -> TermAgree 생성 -> JWTToken 발급 -> RefreshToken 발급 -> LoginResponse 반환
    Case2: Carry 서비스에 등록된 사용자가 있다! (로그인 Case)
    -> User 조회 -> JWTToken 발급 -> RefreshToken 발급 -> LoginResponse 반환
    */
    public Mono<LoginResponse> manageUser(String authorizationCode, String redirectUri) {
        return kakaoOAuthService.getKakaoAccessToken(authorizationCode, redirectUri)
            .flatMap(this::processKakaoAuthentication);
    }

    private Mono<LoginResponse> processKakaoAuthentication(
        KakaoOAuthToken kakaoOAuthToken) {
        return kakaoOAuthService.getKakaoUserInfo(kakaoOAuthToken.getAccessToken())
            .flatMap(kakaoOAuthResource ->
                userService.getUserByKakaoId(kakaoOAuthResource.getId())
                    .flatMap(this::handleExistingUser)
                    .switchIfEmpty(Mono.defer(
                        () -> handleNewUser(kakaoOAuthResource, kakaoOAuthToken.getAccessToken()))
                    )
            );
    }

    private Mono<LoginResponse> handleExistingUser(User user) {
        return generateLoginResponse(user.getId());
    }

    private Mono<LoginResponse> handleNewUser(KakaoOAuthResource kakaoOAuthResource,
        String accessToken) {
        return userService.addUser(User.create(kakaoOAuthResource))
            .flatMap(newUser -> processKakaoTerms(newUser, accessToken)
                .then(generateLoginResponse(newUser.getId())));
    }

    private Mono<Void> processKakaoTerms(User user, String accessToken) {
        return kakaoOAuthService.getUserAgreeTerms(accessToken)
            .flatMap(kakaoUserTermsResponse -> Flux.fromIterable(
                    kakaoUserTermsResponse.getServiceTerms())
                .flatMap(serviceTerm -> {
                    String[] tagParts = serviceTerm.getTag().split("/");
                    String code = tagParts[0];
                    tagParts = tagParts[1].split("_");
                    LocalDate createdAt = LocalDate.parse(tagParts[0]);
                    int versionCount = Integer.parseInt(tagParts[1]);
                    return termService.getTermIdByCodeAndVersion(code, versionCount, createdAt)
                        .flatMap(term -> termAgreementService.manageTermAgreement(user.getId(),
                            term.getId(),
                            serviceTerm.getAgreed()));
                }).then());
    }

    private Mono<LoginResponse> generateLoginResponse(Long userId) {
        return termAgreementService.getTermAgreementsByUserId(userId)
            .filter(TermAgreement::getAgreeYn)
            .map(TermAgreement::getTermId)
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
                return termAgreementService.getTermAgreementsByUserId(userId)
                    .filter(TermAgreement::getAgreeYn)
                    .map(TermAgreement::getTermId)
                    .collectList()
                    .flatMap(acceptedTermIds -> {
                        JWTTokenResponse tokenResponse = jwtHelper.sign(userId, acceptedTermIds,
                            refreshTokenData.getValue(), refreshTokenData.getExpiryAt());
                        return Mono.defer(() -> Mono.just(LoginResponse.from(tokenResponse)));
                    });
            });
    }

}
