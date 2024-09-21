package org.example.coin_laundry_app_backend.review.application;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.review.presentation.payload.response.ReviewCommonResponse;
import org.example.coin_laundry_app_backend.review.presentation.payload.response.ReviewMetadataResponse;
import org.example.coin_laundry_app_backend.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public Mono<ReviewMetadataResponse> getReviewMetadataByLaundryId(Long laundryId) {
        return reviewRepository.getReviewMetadataByLaundromatId(laundryId);
    }

    @Transactional(readOnly = true)
    public Flux<ReviewCommonResponse> getReviewsByLaundryId(Long laundryId) {
        return reviewRepository.getReviewsByLaundromatId(laundryId);
    }
}
