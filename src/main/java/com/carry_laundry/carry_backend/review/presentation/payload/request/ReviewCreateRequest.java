package com.carry_laundry.carry_backend.review.presentation.payload.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewCreateRequest {

    @Positive(message = "세탁소 ID는 1 이상이어야 합니다.")
    private Long laundromatId;
    private String comment;
    @Min(value = 1, message = "리뷰 평점은 1점 이상이어야 합니다.")
    @Max(value = 5, message = "리뷰 평점은 5점 이하여야 합니다.")
    private Integer reviewRating;
    private List<UUID> mediaAccessKeys;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    public void setMediaAccessKeys(List<UUID> mediaAccessKeys) {
        this.mediaAccessKeys = mediaAccessKeys != null ? mediaAccessKeys : List.of();
    }
}
