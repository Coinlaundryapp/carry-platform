package org.example.coin_laundry_app_backend.geo.external;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Setter;
import org.example.coin_laundry_app_backend.geo.application.service.GeocodingService;
import org.example.coin_laundry_app_backend.geo.application.service.model.GeoModel;
import org.example.coin_laundry_app_backend.geo.application.service.model.JibunGeoModel;
import org.example.coin_laundry_app_backend.geo.application.service.model.RoadGeoCoding;
import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import org.example.coin_laundry_app_backend.geo.external.recrod.GetGeocodingResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Setter
@Service
@ConfigurationProperties(prefix = "settings.external.geocoding-api.naver")
public class NaverGeocodingService implements GeocodingService {

    private String baseUrl;
    private String apiKeyId;
    private String apiKey;
    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(headers -> {
            headers.add("X-NCP-APIGW-API-KEY-ID", apiKeyId);
            headers.add("X-NCP-APIGW-API-KEY", apiKey);
        }).build();
    }

    @Override
    public Mono<List<GeoModel>> getGeocoding(String address) {
        return webClient.get().uri(uriBuilder -> uriBuilder.queryParam("query", address).build())
            .retrieve().bodyToMono(GetGeocodingResponse.class).map(
                response -> response.addresses().stream().map(
                    responseAddress -> new GeoModel(responseAddress.jibunAddress(),
                        responseAddress.roadAddress(),
                        new EPSG4326Coordinate(Double.parseDouble(responseAddress.y()),
                            Double.parseDouble(responseAddress.x())),
                        responseAddress.getAddressElementNameOrNull(
                            GetGeocodingResponse.AddressType.SIDO),
                        responseAddress.getAddressElementNameOrNull(
                            GetGeocodingResponse.AddressType.SIGUGUN),
                        responseAddress.getAddressElementNameOrNull(
                            GetGeocodingResponse.AddressType.DONGMYUN),
                        responseAddress.getAddressElementNameOrNull(
                            GetGeocodingResponse.AddressType.RI),
                        responseAddress.getAddressElementNameOrNull(
                            GetGeocodingResponse.AddressType.ROAD_NAME),
                        responseAddress.getAddressElementNameOrNull(
                            GetGeocodingResponse.AddressType.BUILDING_NAME),
                        responseAddress.getAddressElementNameOrNull(
                            GetGeocodingResponse.AddressType.LAND_NUMBER),
                        responseAddress.getAddressElementNameOrNull(
                            GetGeocodingResponse.AddressType.POSTAL_CODE))).toList());
    }

    @Override
    public Mono<List<JibunGeoModel>> getJibunGeocoding(String address) {
        return getGeocoding(address).map(
            geoModels -> geoModels.stream().filter(geoModel -> !geoModel.jibunAddress().isEmpty())
                .map(geoModel -> new JibunGeoModel(geoModel.jibunAddress(),
                    geoModel.coordinate(),
                    geoModel.sido(),
                    geoModel.sigungu(),
                    geoModel.dongmyun(),
                    geoModel.ri(),
                    geoModel.buildingName(),
                    geoModel.landNumber(),
                    geoModel.postalCode()))
                .toList());
    }

    @Override
    public Mono<List<RoadGeoCoding>> getRoadGeocoding(String address) {
        return getGeocoding(address)
            .map(geoModels -> geoModels.stream()
                .filter(geoModel -> !geoModel.roadAddress().isEmpty())
                .map(geoModel -> new RoadGeoCoding(
                    geoModel.roadAddress(),
                    geoModel.coordinate(),
                    geoModel.sido(),
                    geoModel.sigungu(),
                    geoModel.dongmyun(),
                    geoModel.ri(),
                    geoModel.roadName(),
                    geoModel.buildingName(),
                    geoModel.landNumber(),
                    geoModel.postalCode()
                )).collect(Collectors.toCollection(ArrayList::new)));
    }
}
