package com.carry_laundry.carry_backend.review.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.media_resource.application.ResourceMetadataService;
import com.carry_laundry.carry_backend.review.application.ReviewService;
import com.carry_laundry.carry_backend.review.presentation.api.ReviewManageSwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/reviews/manage")
@RequiredArgsConstructor
public class ReviewManageController implements ReviewManageSwagger {

    private final ReviewService reviewService;
    // TODO: Must changed to Event Listening
    private final ResourceMetadataService resourceMetadataService;

    @DeleteMapping("/{reviewId}")
    public Mono<ApiCommonResponse<Void>> deleteReview(@AuthenticationPrincipal Long userId,
        @PathVariable Long reviewId) {
        return reviewService.deleteReview(userId, reviewId)
            .flatMap(accessKeys -> resourceMetadataService.updateValidations("review", accessKeys,
                Boolean.FALSE))
            .then(Mono.just(ApiCommonResponse.createSuccessResponse()));
    }
}
