package org.example.coin_laundry_app_backend.user.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTHelper;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTTokenResponse;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.RefreshToken;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.TermAgree;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.User;
import org.example.coin_laundry_app_backend.user.domain.model.value.TermInfo;
import org.example.coin_laundry_app_backend.user.application.record.oauth.KakaoOAuthToken;
import org.example.coin_laundry_app_backend.user.application.record.oauth.KakaoOAuthResource;
import org.example.coin_laundry_app_backend.user.application.record.oauth.KakaoUserTermsResponse.ServiceTerm;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.LoginResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@Transactional
@RequiredArgsConstructor
public class UserSignService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final TermAgreeService termAgreeService;
    private final TermService termService;
    private final KakaoOAuthService kakaoOAuthService;
    private final JWTHelper jwtHelper;

    public Mono<LoginResponse> trySignIn(String authorizationCode) {
        return kakaoOAuthService.getKakaoAccessToken(authorizationCode)
            .flatMap(this::processKakaoAuthentication);
    }

    private Mono<LoginResponse> processKakaoAuthentication(KakaoOAuthToken kakaoAuthResponse) {
        return kakaoOAuthService.getKakaoUserInfo(kakaoAuthResponse.getAccessToken())
            .flatMap(kakaoUserResponse ->
                userService.getUserByKakaoId(kakaoUserResponse.getId())
                    .flatMap(this::handleExistingUser)
                    .switchIfEmpty(Mono.defer(
                        () -> handleNewUser(kakaoUserResponse, kakaoAuthResponse.getAccessToken()))
                    )
            );
    }

    private Mono<LoginResponse> handleExistingUser(User user) {
        return generateLoginResponse(user.getId());
    }

    private Mono<LoginResponse> handleNewUser(KakaoOAuthResource kakaoOAuthResource, String accessToken) {
        return userService.addUser(User.create(kakaoOAuthResource))
            .flatMap(newUser -> processKakaoTerms(newUser, accessToken)
                .then(generateLoginResponse(newUser.getId())));
    }

    private Mono<Void> processKakaoTerms(User user, String accessToken) {
        return kakaoOAuthService.getUserAgreeTerms(accessToken)
            .flatMap(kakaoUserTermsResponse -> {
                List<Mono<TermAgree>> termAgreeMono = kakaoUserTermsResponse.getServiceTerms()
                    .stream()
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
