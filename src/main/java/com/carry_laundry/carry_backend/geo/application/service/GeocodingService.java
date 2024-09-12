package com.carry_laundry.carry_backend.geo.application.service;

import com.carry_laundry.carry_backend.geo.application.service.model.GeoModel;
import com.carry_laundry.carry_backend.geo.application.service.model.JibunGeoModel;
import com.carry_laundry.carry_backend.geo.application.service.model.RoadGeoCoding;
import java.util.List;
import reactor.core.publisher.Mono;

public interface GeocodingService {

    Mono<List<GeoModel>> getGeocoding(String address);

    Mono<List<JibunGeoModel>> getJibunGeocoding(String address);

    Mono<List<RoadGeoCoding>> getRoadGeocoding(String address);
}
