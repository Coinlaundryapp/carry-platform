package com.carry_laundry.carry_backend.laundromat.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.laundromat.application.LaundromatService;
import com.carry_laundry.carry_backend.laundromat.presentation.api.LaundromatSwagger;
import com.carry_laundry.carry_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import com.carry_laundry.carry_backend.review.application.ReviewService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
            .concatMap(laundromatCommonResponse -> reviewService.getReviewStatisticByLaundryId(
                laundromatCommonResponse.getId()).map(reviewStatisticResponse -> {
                laundromatCommonResponse.setReviewStatistic(reviewStatisticResponse.averageRating(),
                    reviewStatisticResponse.reviewCount());
                return laundromatCommonResponse;
            })).collectList().map(ApiCommonResponse::createSuccessResponse);
    }
}
