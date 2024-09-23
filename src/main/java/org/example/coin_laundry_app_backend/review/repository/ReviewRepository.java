package org.example.coin_laundry_app_backend.review.repository;

import org.example.coin_laundry_app_backend.review.presentation.payload.response.ReviewCommonResponse;
import org.example.coin_laundry_app_backend.review.presentation.payload.response.ReviewStatisticResponse;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReviewRepository {

    Mono<ReviewStatisticResponse> getReviewStaticByLaundromatId(Long laundromatId);

    Flux<ReviewCommonResponse> getReviewsByLaundromatId(Long laundromatId);
}
