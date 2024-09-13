package org.example.coin_laundry_app_backend.laundry.application;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.laundry.presentation.payload.response.LaundryCommonResponse;
import org.example.coin_laundry_app_backend.laundry.repository.LaundryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class LaundryService {

    private final LaundryRepository laundryRepository;

    public Flux<LaundryCommonResponse> findByLocationAndDistance(double latitude, double longitude,
        double distance) {
        return laundryRepository.findByLocationAndDistance(latitude, longitude, distance);
    }
}
