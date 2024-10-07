package com.carry_laundry.carry_backend.review.repository;

import com.carry_laundry.carry_backend.review.domain.entity.Review;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewCommonResponse;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewStatisticResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReviewRepository {

    Mono<Review> save(@NonNull Review review);

    Mono<ReviewStatisticResponse> getReviewStaticByLaundromatId(Long laundromatId);

    Flux<ReviewCommonResponse> getReviewsByLaundromatId(Long laundromatId);

    Mono<Void> deleteById(Long reviewId);
}
