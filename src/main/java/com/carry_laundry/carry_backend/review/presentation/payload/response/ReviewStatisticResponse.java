package com.carry_laundry.carry_backend.review.presentation.payload.response;

public record ReviewStatisticResponse(
    Long laundryId,
    Long reviewCount,
    Double averageRating
) {

}
