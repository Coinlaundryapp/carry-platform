package org.example.coin_laundry_app_backend.geo.controller;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import org.example.coin_laundry_app_backend.geo.service.GeocodingService;
import org.example.coin_laundry_app_backend.geo.service.ReverseGeocodingService;
import org.example.coin_laundry_app_backend.geo.service.model.GeoModel;
import org.example.coin_laundry_app_backend.geo.service.model.ReverseGeoModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * TODO geo coding POC를 위한 controller 해당 컨트롤러는 POC를 위한 것이므로 삭제 예정
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class GeoPocController {

    private final ReverseGeocodingService reverseGeocodingService;
    private final GeocodingService geocodingService;

    @GetMapping("/gc")
    Mono<List<GeoModel>> getGeoCoding(String address) {
        return geocodingService.getGeocoding(address);
    }

    @GetMapping("/rgc")
    Mono<ReverseGeoModel> getReverseGeocoding(EPSG4326Coordinate coordinate) {
        return reverseGeocodingService.getReverseGeocoding(coordinate);
    }
}
