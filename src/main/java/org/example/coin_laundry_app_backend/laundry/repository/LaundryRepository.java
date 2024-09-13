package org.example.coin_laundry_app_backend.laundry.repository;

import org.example.coin_laundry_app_backend.laundry.presentation.payload.response.LaundryCommonResponse;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface LaundryRepository {

    Flux<LaundryCommonResponse> findByLocationAndDistance(double latitude, double longitude,
        double distance);

}
