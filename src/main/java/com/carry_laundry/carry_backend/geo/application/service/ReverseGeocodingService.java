package com.carry_laundry.carry_backend.geo.application.service;

import com.carry_laundry.carry_backend.geo.application.service.model.ReverseGeoModel;
import com.carry_laundry.carry_backend.geo.domain.model.EPSG4326Coordinate;
import reactor.core.publisher.Mono;

public interface ReverseGeocodingService {

    Mono<ReverseGeoModel> getReverseGeocoding(EPSG4326Coordinate coordinate);
}
