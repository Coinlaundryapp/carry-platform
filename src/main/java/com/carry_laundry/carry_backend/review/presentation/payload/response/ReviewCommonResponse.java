package com.carry_laundry.carry_backend.review.presentation.payload.response;

import com.carry_laundry.carry_backend.common.presentation.payload.MediaCommonResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewCommonResponse {

    private Long id;
    private String laundromatName;
    private String username;
    private List<MediaCommonResponse> mediaResources;
    private String content;
    private Integer rating;
    private String createdAt;
    private String updatedAt;

    @Builder
    protected ReviewCommonResponse(Long id, String laundromatName, String username,
        List<MediaCommonResponse> mediaResources, String content, Integer rating,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.laundromatName = laundromatName;
        this.username = username;
        this.mediaResources = mediaResources;
        this.content = content;
        this.rating = rating;
        this.createdAt = createdAt.toString();
        this.updatedAt = updatedAt.toString();
    }
}


