package org.example.coin_laundry_app_backend.geo.application.service;

import org.example.coin_laundry_app_backend.geo.application.service.model.GeoModel;
import org.example.coin_laundry_app_backend.geo.application.service.model.JibunGeoModel;
import org.example.coin_laundry_app_backend.geo.application.service.model.RoadGeoCoding;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GeocodingService {
    Mono<List<GeoModel>> getGeocoding(String address);

    Mono<List<JibunGeoModel>> getJibunGeocoding(String address);

    Mono<List<RoadGeoCoding>> getRoadGeocoding(String address);
}
