package com.carry_laundry.carry_backend.review.application;

import com.carry_laundry.carry_backend.review.domain.entity.Review;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewCommonResponse;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewStatisticResponse;
import com.carry_laundry.carry_backend.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public Mono<Review> saveReview(Long userId, Long laundromatId, String comment,
        Integer reviewRating) {
        return reviewRepository.save(Review.builder()
            .userId(userId)
            .laundromatId(laundromatId)
            .comment(comment)
            .reviewRating(reviewRating)
            .build());
    }

    @Transactional(readOnly = true)
    public Mono<ReviewStatisticResponse> getReviewStatisticByLaundryId(Long laundryId) {
        return reviewRepository.getReviewStaticByLaundromatId(laundryId);
    }

    @Transactional(readOnly = true)
    public Flux<ReviewCommonResponse> getReviewsByLaundryId(Long laundryId) {
        return reviewRepository.getReviewsByLaundromatId(laundryId);
    }
}
