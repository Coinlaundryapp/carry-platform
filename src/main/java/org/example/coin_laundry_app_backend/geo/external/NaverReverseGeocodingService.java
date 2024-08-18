package org.example.coin_laundry_app_backend.geo.external;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import org.example.coin_laundry_app_backend.geo.application.service.ReverseGeocodingService;
import org.example.coin_laundry_app_backend.geo.application.service.model.ReverseGeoModel;
import org.example.coin_laundry_app_backend.geo.external.formatter.EPSG4326CoordinateBuilderForApi;
import org.example.coin_laundry_app_backend.geo.external.recrod.GetReverseGeocodingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Setter
@Service
@ConfigurationProperties(prefix = "settings.external.reverse-geocoding-api.naver")
public class NaverReverseGeocodingService implements ReverseGeocodingService {

    private String baseUrl;
    private String apiKeyId;
    private String apiKey;
    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders((headers) -> {
                            headers.add("X-NCP-APIGW-API-KEY-ID", apiKeyId);
                            headers.add("X-NCP-APIGW-API-KEY", apiKey);
                        }
                )
                .build();
    }

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
