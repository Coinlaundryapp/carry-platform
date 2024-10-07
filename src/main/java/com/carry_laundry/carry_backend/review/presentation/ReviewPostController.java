package com.carry_laundry.carry_backend.review.presentation;

import com.carry_laundry.carry_backend.review.application.ReviewService;
import com.carry_laundry.carry_backend.review.presentation.payload.request.ReviewCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/reviews/post")
@RequiredArgsConstructor
public class ReviewPostController {

    private final ReviewService reviewService;

    @PostMapping("/{laundromatId}")
    public Mono<Void> postReview(@AuthenticationPrincipal Long userId,
        @PathVariable Long laundromatId,
        @Valid @RequestBody ReviewCreateRequest request) {
        return reviewService.saveReview(userId, laundromatId, request.getComment(),
                request.getReviewRating())
            .then();
    }
}
