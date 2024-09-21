package org.example.coin_laundry_app_backend.laundromat.presentation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.laundromat.application.LaundromatService;
import org.example.coin_laundry_app_backend.laundromat.presentation.api.LaundromatSwagger;
import org.example.coin_laundry_app_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import org.example.coin_laundry_app_backend.review.application.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/laundromats")
@RequiredArgsConstructor
public class LaundromatController implements LaundromatSwagger {

    private static final Integer DEFAULT_DISTANCE = 3000;

    private final LaundromatService laundromatService;
    private final ReviewService reviewService;

    @GetMapping
    public Mono<ApiCommonResponse<List<LaundromatCommonResponse>>> getLaundromatList(
        @RequestParam Double latitude, @RequestParam Double longitude) {

        return laundromatService.findByLocationAndDistance(latitude, longitude, DEFAULT_DISTANCE)
            .flatMap(laundromatCommonResponse -> reviewService.getReviewMetadataByLaundryId(
                    laundromatCommonResponse.getId())
                .map(reviewMetadataResponse -> {
                    laundromatCommonResponse.setReviewMetadata(
                        reviewMetadataResponse.averageRating(),
                        reviewMetadataResponse.reviewCount());
                    return laundromatCommonResponse;
                })).collectList().map(ApiCommonResponse::createSuccessResponse);
    }
}
