package org.example.coin_laundry_app_backend.user.domain.service;

import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUnlinkResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUserResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
// 고민 포인트: 추상화?
public class KakaoOAuthService {

    // TODO: baseURL 주입 방식 변경
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
