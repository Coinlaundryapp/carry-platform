package org.example.coin_laundry_app_backend.geo.service;

import org.example.coin_laundry_app_backend.geo.service.model.GeoModel;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GeocodingService {
    Mono<List<GeoModel>> getGeocoding(String address);
}
