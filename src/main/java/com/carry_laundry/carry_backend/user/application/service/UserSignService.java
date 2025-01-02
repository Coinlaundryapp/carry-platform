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
import java.util.List;
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


    /*
    전반적인 로그인 로직은 아래와 같다.
    authorizationCode -> accessToken 획득 -> KakaoUserInfo 획득 -> User 조회 -> 분기 발생
    Case1: Carry 서비스에 등록된 사용자가 없다! (회원가입 Case)
    -> User 생성 -> KakaoTermAgree 조회 -> TermAgree 생성 -> JWTToken 발급 -> RefreshToken 발급 -> LoginResponse 반환
    Case2: Carry 서비스에 등록된 사용자가 있다! (로그인 Case)
    -> User 조회 -> JWTToken 발급 -> RefreshToken 발급 -> LoginResponse 반환
    */

    /**
     * 카카오 인증 -> (없으면 회원 가입, 있으면 로그인) -> JWT/RefreshToken 발급 후 LoginResponse 반환
     *
     * @param authorizationCode 카카오 인증 코드
     * @param redirectUri       리다이렉트 URI
     * @return JWT/RefreshToken 발급 후 LoginResponse
     */
    public Mono<LoginResponse> loginOrSignUpWithKakao(String authorizationCode,
        String redirectUri) {
        return kakaoOAuthService.getKakaoAccessToken(authorizationCode, redirectUri)
            .flatMap(this::processKakaoAuthentication);
    }

    /**
     * 카카오 토큰 -> 카카오 사용자 정보 조회 -> Carry 사용자 조회 -> Carry 사용자가 없으면 회원 가입, 있으면 로그인
     *
     * @param kakaoOAuthToken 카카오 OAuth 토큰
     * @return JWT/RefreshToken 발급 후 LoginResponse
     */
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

    /**
     * Carry 사용자가 이미 존재하는 경우 처리
     *
     * @param user Carry 사용자
     * @return JWT/RefreshToken 발급 후 LoginResponse
     */
    private Mono<LoginResponse> handleExistingUser(User user) {
        return generateLoginResponse(user.getId());
    }

    /**
     * 신규 유저 처리 1. Carry 사용자 생성 2. 카카오 약관 동의 처리 3. JWT/RefreshToken 발급 후 LoginResponse 반환
     *
     * @param kakaoOAuthResource 카카오 사용자 정보
     * @param accessToken        카카오 액세스 토큰
     * @return JWT/RefreshToken 발급 후 LoginResponse
     */
    private Mono<LoginResponse> handleNewUser(KakaoOAuthResource kakaoOAuthResource,
        String accessToken) {
        return userService.addUser(User.create(kakaoOAuthResource))
            .flatMap(newUser -> processKakaoTerms(newUser, accessToken)
                .then(generateLoginResponse(newUser.getId())));
    }

    /**
     * 카카오 약관 동의 처리 -> 서버 약관 매핑 & 동의 여부 저장
     *
     * @param user        Carry 사용자
     * @param accessToken 카카오 액세스 토큰
     * @return 약관 동의 처리 결과
     */
    private Mono<Void> processKakaoTerms(User user, String accessToken) {
        return kakaoOAuthService.getUserAgreeTerms(accessToken)
            .flatMap(kakaoUserTermsResponse -> Flux.fromIterable(
                    kakaoUserTermsResponse.getServiceTerms())
                .flatMap(serviceTerm -> {
                    // 카카오 약관 태그 형식 "{code}/{createdAt}_{versionCount}"
                    // 예시: "SERVICE_TERMS/2021-08-01_1"
                    // -> code: SERVICE_TERMS, createdAt: 2021-08-01, versionCount: 1
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

    /**
     * 사용자가 동의한 약관 ID 목록 조회 (JWT 토큰 발급을 위한 전처리)
     *
     * @param userId 사용자 ID
     * @return 사용자가 동의한 약관 ID 목록
     */
    private Mono<List<Long>> getAcceptedTerms(Long userId) {
        return termAgreementService.getTermAgreementsByUserId(userId)
            .filter(TermAgreement::getAgreeYn)
            .map(TermAgreement::getTermId)
            .collectList();
    }

    /**
     * JWT/RefreshToken 발급 후 LoginResponse 반환
     *
     * @param userId 사용자 ID
     * @return JWT/RefreshToken 발급 후 LoginResponse
     */
    private Mono<LoginResponse> generateLoginResponse(Long userId) {
        return getAcceptedTerms(userId)
            .flatMap(acceptedTerms -> {
                JWTTokenResponse jwtTokenResponse = jwtHelper.sign(userId, acceptedTerms);
                LoginResponse response = LoginResponse.from(jwtTokenResponse);
                return refreshTokenService.addRefreshToken(
                        RefreshToken.of(userId, jwtTokenResponse))
                    .thenReturn(response);
            });
    }

    /**
     * RefreshToken을 이용한 토큰 재발급
     *
     * @param refreshToken RefreshToken
     * @return JWT/RefreshToken 발급 후 LoginResponse
     */
    public Mono<LoginResponse> reissue(String refreshToken) {
        return refreshTokenService.findRefreshTokenByValue(refreshToken)
            .flatMap(refreshTokenData -> {
                Long userId = refreshTokenData.getUserId();
                return getAcceptedTerms(userId)
                    .map(acceptedTermIds -> jwtHelper.sign(
                        userId,
                        acceptedTermIds,
                        refreshTokenData.getValue(),
                        refreshTokenData.getExpiryAt()
                    ))
                    .map(LoginResponse::from);
            });
    }

}
