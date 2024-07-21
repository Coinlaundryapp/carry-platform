package org.example.coin_laundry_app_backend.geo.service;

import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import org.example.coin_laundry_app_backend.geo.service.model.ReverseGeoModel;
import reactor.core.publisher.Mono;

public interface ReverseGeocodingService {

    Mono<ReverseGeoModel> getReverseGeocoding(EPSG4326Coordinate coordinate);
}
