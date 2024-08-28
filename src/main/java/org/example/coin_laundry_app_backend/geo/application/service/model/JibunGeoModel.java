package org.example.coin_laundry_app_backend.geo.application.service.model;

import lombok.NonNull;
import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;

public record JibunGeoModel(
    String jibunAddress,
    @NonNull EPSG4326Coordinate coordinate,
    String sido,
    String sigungu,
    String dongmyun,
    String ri,
    String buildingName,
    String landNumber,
    String postalCode
) {}
