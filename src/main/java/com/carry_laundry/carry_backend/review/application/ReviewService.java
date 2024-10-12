package com.carry_laundry.carry_backend.review.application;

import com.carry_laundry.carry_backend.common.presentation.payload.CursorPaginationResponse;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewCommonResponse;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewStatisticResponse;
import com.carry_laundry.carry_backend.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public Mono<ReviewStatisticResponse> getReviewStatisticByLaundryId(Long laundryId) {
        return reviewRepository.getReviewStaticByLaundromatId(laundryId);
    }

    @Transactional(readOnly = true)
    public Mono<CursorPaginationResponse<ReviewCommonResponse>> getReviewsByLaundryId(
        Long laundromatId, Long cursor, Integer size) {
        if (size < 1) {
            throw new IllegalArgumentException("Size must be greater than 0");
        }
        return reviewRepository.getReviewsByLaundromatId(laundromatId, cursor, size)
            .collectList()
            .map(reviewCommonResponses -> CursorPaginationResponse.fromData(reviewCommonResponses,
                size));
    }

}
