package org.example.coin_laundry_app_backend.user.domain.service;

import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoAuthenticationResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUnlinkResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUserResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUserTermsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
// 고민 포인트: 추상화?
public class KakaoOAuthService {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public KakaoOAuthService(@Value("${oauth.kakao.client-id}") String clientId,
        @Value("${oauth.kakao.client-secret}") String clientSecret,
        @Value("${oauth.kakao.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public Mono<KakaoAuthenticationResponse> getKakaoAccessToken(String code) {
        return createWebClient("https://kauth.kakao.com/oauth/token")
            .post()
            .body(BodyInserters
                .fromFormData("grant_type", "authorization_code")
                .with("client_id", clientId)
                .with("redirect_uri", redirectUri)
                .with("code", code)
                .with("client_secret", clientSecret))
            .retrieve()
            .bodyToMono(KakaoAuthenticationResponse.class)
            .transform(this::handleError);
    }

    public Mono<KakaoUserResponse> getKakaoUserInfo(String accessToken) {
        return createWebClient("https://kapi.kakao.com/v2/user/me", accessToken,
            MediaType.APPLICATION_FORM_URLENCODED)
            .get()
            .retrieve()
            .bodyToMono(KakaoUserResponse.class)
            .transform(this::handleError);
    }

    public Mono<KakaoUserTermsResponse> getUserAgreeTerms(String accessToken) {
        return createWebClient("https://kapi.kakao.com/v2/user/service_terms", accessToken)
            .get()
            .retrieve()
            .bodyToMono(KakaoUserTermsResponse.class)
            .transform(this::handleError);
    }

    public Mono<KakaoUnlinkResponse> unlinkKakao(String accessToken) {
        return createWebClient("https://kapi.kakao.com/v1/user/unlink", accessToken,
            MediaType.APPLICATION_FORM_URLENCODED)
            .post()
            .retrieve()
            .bodyToMono(KakaoUnlinkResponse.class)
            .transform(this::handleError);
    }

    private WebClient createWebClient(String baseUrl) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
            .build();
    }

    private WebClient createWebClient(String baseUrl, String accessToken) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders(
                header -> header.setBearerAuth(accessToken)
            ).build();
    }

    private WebClient createWebClient(String baseUrl, String accessToken, MediaType contentType) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders(
                header -> {
                    header.setBearerAuth(accessToken);
                    header.setContentType(contentType);
                }
            ).build();
    }

    private <T> Mono<T> handleError(Mono<T> mono) {
        return mono.onErrorResume(e ->
            Mono.error(new IllegalArgumentException(e.getMessage()))
        );
    }
}
