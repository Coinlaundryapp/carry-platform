package org.example.coin_laundry_app_backend.laundromat.application;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import org.example.coin_laundry_app_backend.laundromat.repository.LaundromatRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class LaundromatService {

    private final LaundromatRepository laundromatRepository;

    public Flux<LaundromatCommonResponse> findByLocationAndDistance(double latitude,
        double longitude, double distance) {
        return laundromatRepository.findByLocationAndDistance(latitude, longitude, distance);
    }
}
