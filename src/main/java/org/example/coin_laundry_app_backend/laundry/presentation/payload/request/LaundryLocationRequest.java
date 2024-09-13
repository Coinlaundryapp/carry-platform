package org.example.coin_laundry_app_backend.laundry.presentation.payload.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "세탁소 위치 조회 요청")
public class LaundryLocationRequest {

    @NotNull
    @Schema(description = "위도", example = "37.123456")
    private Double latitude;
    @NotNull
    @Schema(description = "경도", example = "127.123456")
    private Double longitude;
}
