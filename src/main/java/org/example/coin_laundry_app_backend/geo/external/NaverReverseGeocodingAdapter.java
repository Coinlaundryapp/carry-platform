package org.example.coin_laundry_app_backend.geo.external;

import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import org.example.coin_laundry_app_backend.geo.service.ReverseGeocodingService;
import org.example.coin_laundry_app_backend.geo.service.model.ReverseGeoModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class NaverReverseGeocodingAdapter implements ReverseGeocodingService {

    NaverReverseGeocodingAdapter(
        @Value("${naver.api.id}") final String naverApiKeyId,
        @Value("${naver.api.key}") final String naverApiKey
    ) {
        this.webClient = WebClient.builder()
            .baseUrl("https://naveropenapi.apigw.ntruss.com/map-reversegeocode/v2/gc")
            .defaultHeaders((headers) -> {
                    headers.add("X-NCP-APIGW-API-KEY-ID", naverApiKeyId);
                    headers.add("X-NCP-APIGW-API-KEY", naverApiKey);
                }
            )
            .build();
    }

    private final WebClient webClient;

    @Override
    public Mono<ReverseGeoModel> getReverseGeocoding(final EPSG4326Coordinate coordinate) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .queryParam("coords", EPSG4326CoordinateBuilderForApi.build(coordinate))
                .queryParam("output", "json")
                .queryParam("orders", "addr")
                .queryParam("sourcecrs", "EPSG:4326")
                .build()
            )
            .retrieve()
            .bodyToMono(GetReverseGeocodingResponse.class)
            .map(response -> {
                var address = response.results().get(0).region();
                return new ReverseGeoModel(
                    address.area0().name(),
                    address.area1().name(),
                    address.area2().name(),
                    address.area3().name()
                );
            });
    }
}
