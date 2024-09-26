package org.example.coin_laundry_app_backend.laundromat.repository;

import org.example.coin_laundry_app_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import reactor.core.publisher.Flux;

public interface LaundromatCustomRepository {

    Flux<LaundromatCommonResponse> findByLocationAndDistance(double latitude, double longitude,
                                                             double distance);
}
