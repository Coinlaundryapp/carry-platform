package org.example.coin_laundry_app_backend.user.domain.service;

import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoAuthenticationResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUnlinkResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUserResponse;
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

    public KakaoOAuthService(@Value("oauth.kakao.client-id") String clientId,
        @Value("oauth.kakao.client-secret") String clientSecret,
        @Value("oauth.kakao.redirect-uri") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public Mono<KakaoAuthenticationResponse> getKakaoAccessToken(String code) {
        WebClient webClient = WebClient.builder().baseUrl("https://kauth.kakao.com/oauth/token")
            .defaultHeaders(
                headers -> headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED)).build();
        return webClient.post()
            .body(BodyInserters
                .fromFormData("grant_type", "authorization_code")
                .with("client_id", clientId)
                .with("redirect_uri", redirectUri)
                .with("code", code)
                .with("client_secret", clientSecret))
            .retrieve()
            .bodyToMono(KakaoAuthenticationResponse.class);
    }

    public Mono<KakaoUserResponse> getKakaoUserInfo(String accessToken) {
        return getWebClient("https://kapi.kakao.com/v2/user/me", accessToken).get()
            .retrieve()
            .bodyToMono(KakaoUserResponse.class)
            .onErrorResume(
                e -> Mono.fromCallable(() -> new IllegalArgumentException(e.getMessage()))
                    .cast(KakaoUserResponse.class));
    }

    public Mono<KakaoUnlinkResponse> unlinkKakao(String accessToken) {
        return getWebClient("https://kapi.kakao.com/v1/user/unlink", accessToken).post()
            .retrieve()
            .bodyToMono(KakaoUnlinkResponse.class)
            .onErrorResume(
                e -> Mono.fromCallable(() -> new IllegalArgumentException(e.getMessage()))
                    .cast(KakaoUnlinkResponse.class));
    }

    private WebClient getWebClient(String baseUrl, String accessToken) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders(
                header -> {
                    header.setBearerAuth(accessToken);
                    header.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                }
            ).build();
    }

}
