package org.example.coin_laundry_app_backend.laundromat.application;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.geo.application.service.record.GeoCoordinate;
import org.example.coin_laundry_app_backend.laundromat.domain.entity.Laundromat;
import org.example.coin_laundry_app_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import org.example.coin_laundry_app_backend.laundromat.repository.LaundromatRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class LaundromatService {

    private final LaundromatRepository laundromatRepository;

    public Flux<LaundromatCommonResponse> findByLocationAndDistance(double latitude,
        double longitude, double distance) {
        return laundromatRepository.findByLocationAndDistance(latitude, longitude, distance);
    }

//    public Mono<GeoCoordinate> getCoordinateById(Long id) {
//        return laundromatRepository.findById(id).map(laundromat ->
//                new GeoCoordinate(
//                        laundromat.getLocationCoordinate().getCoordinate().x,
//                        laundromat.getLocationCoordinate().getCoordinate().y
//                )
//        );
//    }

    public Mono<Laundromat> getLaundromatById(Long id) {
        return laundromatRepository.findById(id);
    }
}
