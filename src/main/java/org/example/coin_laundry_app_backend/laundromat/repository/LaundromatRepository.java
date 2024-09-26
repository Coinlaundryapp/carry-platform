package org.example.coin_laundry_app_backend.laundromat.repository;

import org.example.coin_laundry_app_backend.laundromat.domain.entity.Laundromat;
import org.example.coin_laundry_app_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import org.example.coin_laundry_app_backend.user.domain.entity.data.RefreshTokenData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface LaundromatRepository extends ReactiveCrudRepository<Laundromat, Long>, LaundromatCustomRepository {
}
