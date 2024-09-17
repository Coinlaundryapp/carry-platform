package org.example.coin_laundry_app_backend.laundry.presentation.payload.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.example.coin_laundry_app_backend.laundry.domain.model.enums.LaundryOption;

@Getter
@Schema(description = "세탁소 공통 응답")
public class LaundryCommonResponse {

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
    // TODO: How Could I get laundryPrice from db?
    @Schema(description = "세탁 가격", example = "5000")
    private int laundryPrice;

    // From LaundryOptionMapping Entity
    @Schema(description = "세탁소 옵션 목록")
    private final List<LaundryOption> options;
    // From LaundryImage Entity
    @Schema(description = "세탁소 이미지 URL 목록")
    private final List<String> imageUrls;

    @Schema(description = "리뷰 평균 평점", example = "4.5")
    private double reviewAverageRating;
    @Schema(description = "리뷰 개수", example = "100")
    private Long reviewCount;

    @Builder
    protected LaundryCommonResponse(Long id, String name, String address, double latitude,
        double longitude, double distance, List<LaundryOption> options, List<String> imageUrls) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distance = distance;
        this.options = options;
        this.imageUrls = imageUrls;
    }

    public void setReviewMetadata(double reviewAverageRating, long reviewCount) {
        this.reviewAverageRating = reviewAverageRating;
        this.reviewCount = reviewCount;
    }

}
