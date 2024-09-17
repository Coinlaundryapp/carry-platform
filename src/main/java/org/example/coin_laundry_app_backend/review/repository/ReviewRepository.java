package org.example.coin_laundry_app_backend.review.repository;

import org.example.coin_laundry_app_backend.review.presentation.payload.response.ReviewMetadataResponse;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReviewRepository {

    Mono<ReviewMetadataResponse> getReviewMetadataByLaundryId(Long laundryId);
}
