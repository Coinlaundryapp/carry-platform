package org.example.coin_laundry_app_backend.geo.service.model;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;

public record RoadGeoCoding(
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
) {}
