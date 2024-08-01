package org.example.coin_laundry_app_backend.geo.external;

import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import org.example.coin_laundry_app_backend.geo.service.GeocodingService;
import org.example.coin_laundry_app_backend.geo.service.model.GeoModel;
import org.example.coin_laundry_app_backend.geo.service.model.JibunGeoModel;
import org.example.coin_laundry_app_backend.geo.service.model.RoadGeoCoding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NaverGeocodingAdapter implements GeocodingService {

    NaverGeocodingAdapter(
        @Value("${naver.api.id}") final String naverApiKeyId,
        @Value("${naver.api.key}") final String naverApiKey
    ) {
        this.webClient = WebClient.builder()
            .baseUrl("https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode")
            .defaultHeaders((headers) -> {
                    headers.add("X-NCP-APIGW-API-KEY-ID", naverApiKeyId);
                    headers.add("X-NCP-APIGW-API-KEY", naverApiKey);
                }
            )
            .build();
    }

    private final WebClient webClient;

    @Override
    public Mono<List<GeoModel>> getGeocoding(String address) {
        return webClient.get()
            .uri(uriBuilder ->
                uriBuilder
                .queryParam("query", address)
                .build()
            ).retrieve()
            .bodyToMono(GetGeocodingResponse.class)
            .map(response ->
                response.addresses().stream().map(responseAddress -> new GeoModel(
                    responseAddress.jibunAddress(),
                    responseAddress.roadAddress(),
                    new EPSG4326Coordinate(
                        Double.parseDouble(responseAddress.y()),
                        Double.parseDouble(responseAddress.x())
                    ),
                    responseAddress.getAddressElementNameOrNull(GetGeocodingResponse.AddressType.SIDO),
                    responseAddress.getAddressElementNameOrNull(GetGeocodingResponse.AddressType.SIGUGUN),
                    responseAddress.getAddressElementNameOrNull(GetGeocodingResponse.AddressType.DONGMYUN),
                    responseAddress.getAddressElementNameOrNull(GetGeocodingResponse.AddressType.RI),
                    responseAddress.getAddressElementNameOrNull(GetGeocodingResponse.AddressType.ROAD_NAME),
                    responseAddress.getAddressElementNameOrNull(GetGeocodingResponse.AddressType.BUILDING_NAME),
                    responseAddress.getAddressElementNameOrNull(GetGeocodingResponse.AddressType.LAND_NUMBER),
                    responseAddress.getAddressElementNameOrNull(GetGeocodingResponse.AddressType.POSTAL_CODE)
                    )
                ).collect(Collectors.toCollection(ArrayList::new))
            );
    }

    @Override
    public Mono<List<JibunGeoModel>> getJibunGeocoding(String address) {
        return getGeocoding(address)
            .map(geoModels -> geoModels.stream()
                .filter(geoModel -> !geoModel.jibunAddress().isEmpty())
                .map(geoModel -> new JibunGeoModel(
                    geoModel.jibunAddress(),
                    geoModel.coordinate(),
                    geoModel.sido(),
                    geoModel.sigungu(),
                    geoModel.dongmyun(),
                    geoModel.ri(),
                    geoModel.buildingName(),
                    geoModel.landNumber(),
                    geoModel.postalCode()
                )).collect(Collectors.toCollection(ArrayList::new)));
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
