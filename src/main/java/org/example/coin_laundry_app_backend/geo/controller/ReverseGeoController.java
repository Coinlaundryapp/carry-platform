package org.example.coin_laundry_app_backend.geo.controller;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import org.example.coin_laundry_app_backend.geo.service.ReverseGeocodingService;
import org.example.coin_laundry_app_backend.geo.service.model.ReverseGeoModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReverseGeoController {

    private final ReverseGeocodingService reverseGeocodingService;


    @GetMapping("/rgc")
    Mono<ReverseGeoModel> getReverseGeocoding(EPSG4326Coordinate coordinate) {
        return reverseGeocodingService.getReverseGeocoding(coordinate);
    }
}
