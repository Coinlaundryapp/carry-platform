package com.carry_laundry.carry_backend.laundromat.repository;

import com.carry_laundry.carry_backend.laundromat.domain.entity.Laundromat;
import com.carry_laundry.carry_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LaundromatRepository {

    Flux<LaundromatCommonResponse> findByLocationAndDistance(double latitude, double longitude,
        double distance);

    Mono<Laundromat> findById(@NonNull Long id);
}