package com.carry_laundry.carry_backend.review.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.review.application.ReviewService;
import com.carry_laundry.carry_backend.review.presentation.api.ReviewFetchSwagger;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewCommonResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewFetchController implements ReviewFetchSwagger {

    private final ReviewService reviewService;

    @GetMapping
    public Mono<ApiCommonResponse<List<ReviewCommonResponse>>> getReviewsByLaundryId(
        @RequestParam Long laundryId) {
        return reviewService.getReviewsByLaundryId(laundryId)
            .collectList()
            .map(ApiCommonResponse::createSuccessResponse);
    }
}
