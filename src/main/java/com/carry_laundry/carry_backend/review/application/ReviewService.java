package com.carry_laundry.carry_backend.review.application;

import com.carry_laundry.carry_backend.review.domain.entity.Review;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewCommonResponse;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewStatisticResponse;
import com.carry_laundry.carry_backend.review.repository.ReviewMediaResourceRepository;
import com.carry_laundry.carry_backend.review.repository.ReviewRepository;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
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
    private final ReviewMediaResourceRepository reviewMediaResourceRepository;

    public Mono<Review> saveReview(Long userId, Long laundromatId, String comment,
        Integer reviewRating) {
        return reviewRepository.save(Review.builder()
            .userId(userId)
            .laundromatId(laundromatId)
            .comment(comment)
            .reviewRating(reviewRating)
            .build());
    }

    public Mono<List<UUID>> deleteReview(Long userId, Long reviewId) {
        return reviewRepository.findDetailDataById(reviewId)
            .switchIfEmpty(Mono.error(new NoSuchElementException("Review not found")))
            .flatMap(reviewDetailData -> {
                if (!reviewDetailData.userId().equals(userId)) {
                    return Mono.error(new AccessDeniedException("Access denied"));
                }
                return Flux.fromIterable(reviewDetailData.reviewMediaResources())
                    .flatMap(reviewMediaResource -> reviewMediaResourceRepository.deleteById(
                        reviewMediaResource.getId()).thenReturn(reviewMediaResource.getAccessKey()))
                    .collectList()
                    .flatMap(
                        accessKeys -> reviewRepository.deleteById(reviewId).thenReturn(accessKeys));
            });
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
