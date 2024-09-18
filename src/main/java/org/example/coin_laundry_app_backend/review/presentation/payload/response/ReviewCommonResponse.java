package org.example.coin_laundry_app_backend.review.presentation.payload.response;

import java.util.List;

public record ReviewCommonResponse(
    Long id,
    String laundryName,
    String username,
    List<String> images,
    String content,
    Integer rating,
    String createdAt,
    String updatedAt
) {

}
