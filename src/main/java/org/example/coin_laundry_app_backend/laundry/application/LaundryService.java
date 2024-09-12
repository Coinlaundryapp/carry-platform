package org.example.coin_laundry_app_backend.laundry.application;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.laundry.domain.model.entity.Laundry;
import org.example.coin_laundry_app_backend.laundry.repository.LaundryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class LaundryService {

    private final LaundryRepository laundryRepository;

    public Mono<Laundry> save(Laundry laundry) {
        return laundryRepository.save(laundry);
    }

    public Mono<Laundry> findById(Long id) {
        return laundryRepository.findById(id);
    }

    public Flux<Laundry> findByLocationAndDistance(double latitude, double longitude,
        double distance) {
        return laundryRepository.findByLocationAndDistance(latitude, longitude, distance);
    }
}
