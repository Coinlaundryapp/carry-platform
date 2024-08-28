package org.example.coin_laundry_app_backend.geo.application.service;

import org.example.coin_laundry_app_backend.geo.application.service.model.ReverseGeoModel;
import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import reactor.core.publisher.Mono;

public interface ReverseGeocodingService {

    Mono<ReverseGeoModel> getReverseGeocoding(EPSG4326Coordinate coordinate);
}
