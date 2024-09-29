package com.carry_laundry.carry_backend.laundromat.presentation.payload.response;

import com.carry_laundry.carry_backend.common.presentation.payload.MediaCommonResponse;
import com.carry_laundry.carry_backend.laundromat.domain.enums.LaundromatOption;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Schema(description = "세탁소 공통 응답")
public class LaundromatCommonResponse {

    // From Laundry Entity
    @Schema(description = "세탁소 ID", example = "1")
    private final Long id;
    @Schema(description = "세탁소 이름", example = "세탁소 이름")
    private final String name;
    @Schema(description = "세탁소 주소", example = "세탁소 주소")
    private final String address;
    @Schema(description = "세탁소 위도", example = "37.123456")
    private final double latitude;
    @Schema(description = "세탁소 경도", example = "127.123456")
    private final double longitude;
    @Schema(description = "사용자와의 거리 (단위: m)", example = "300.12345")
    private final double distance;
    @Schema(description = "단독 세탁 배송비", example = "5000")
    private final int individualDeliveryFee;
    @Schema(description = "팀 세탁 배송비", example = "4000")
    private int groupDeliveryFee;

    // From LaundryOptionMapping Entity
    @Schema(description = "세탁소 옵션 목록")
    private final List<LaundromatOption> options;
    // From LaundryImage Entity
    @Schema(description = "세탁소 이미지 URL 목록")
    private final List<MediaCommonResponse> mediaResources;

    @Schema(description = "리뷰 평균 평점", example = "4.5")
    private double reviewAverageRating;
    @Schema(description = "리뷰 개수", example = "100")
    private Long reviewCount;

    @Builder
    public LaundromatCommonResponse(Long id, String name, String address, double latitude,
        double longitude, double distance, List<LaundromatOption> options,
        List<MediaCommonResponse> mediaResources) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distance = distance;
        this.individualDeliveryFee = calculateIndividualDeliveryFee((int) distance);
        this.options = options;
        this.mediaResources = mediaResources;
    }

    public void setReviewStatistic(double reviewAverageRating, long reviewCount) {
        this.reviewAverageRating = reviewAverageRating;
        this.reviewCount = reviewCount;
    }

    private int calculateIndividualDeliveryFee(int distance) {
        int fare = 4000;
        if (distance <= 100) {
            return fare;
        } else if (distance < 1000) {
            fare += ((distance - 100) / 100) * 300;
        } else {
            fare += (900 / 100) * 300;
            fare += ((distance - 1000) / 100) * 200;
        }
        return fare;
    }

}
