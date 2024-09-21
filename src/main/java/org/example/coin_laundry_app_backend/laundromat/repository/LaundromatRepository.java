package org.example.coin_laundry_app_backend.laundromat.repository;

import org.example.coin_laundry_app_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface LaundromatRepository {

    Flux<LaundromatCommonResponse> findByLocationAndDistance(double latitude, double longitude,
        double distance);

}
