package com.carry_laundry.carry_backend.review.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.common.presentation.payload.CursorPaginationResponse;
import com.carry_laundry.carry_backend.review.application.ReviewService;
import com.carry_laundry.carry_backend.review.presentation.api.ReviewFetchSwagger;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewCommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewFetchController implements ReviewFetchSwagger {

    private final ReviewService reviewService;

    @GetMapping("/laundromat/{laundromatId}")
    public Mono<ApiCommonResponse<CursorPaginationResponse<ReviewCommonResponse>>> getReviewsByLaundryId(
        @PathVariable Long laundromatId,
        @RequestParam(required = false, defaultValue = "0") Long cursor,
        @RequestParam(required = false, defaultValue = "10") Integer size) {
        return reviewService.getReviewsByLaundryId(laundromatId, cursor, size)
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @GetMapping("/user")
    public Mono<ApiCommonResponse<CursorPaginationResponse<ReviewCommonResponse>>> getReviewsByUserId(
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false, defaultValue = "0") Long cursor,
        @RequestParam(required = false, defaultValue = "10") Integer size) {
        return reviewService.getReviewsByUserId(userId, cursor, size)
            .map(ApiCommonResponse::createSuccessResponse);
    }
}
