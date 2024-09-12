package com.carry_laundry.carry_backend.geo.application.service.model;

import com.carry_laundry.carry_backend.geo.domain.model.EPSG4326Coordinate;
import jakarta.annotation.Nullable;
import lombok.NonNull;

public record GeoModel(
    @NonNull String jibunAddress,
    @NonNull String roadAddress,
    @NonNull EPSG4326Coordinate coordinate,
    @Nullable String sido,
    @Nullable String sigungu,
    @Nullable String dongmyun,
    @Nullable String ri,
    @Nullable String roadName,
    @Nullable String buildingName,
    @Nullable String landNumber,
    @Nullable String postalCode
) {

}
