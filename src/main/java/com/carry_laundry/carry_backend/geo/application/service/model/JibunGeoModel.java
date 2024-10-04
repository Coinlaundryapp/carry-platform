package com.carry_laundry.carry_backend.geo.application.service.model;

import com.carry_laundry.carry_backend.geo.domain.model.EPSG4326Coordinate;
import lombok.NonNull;

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
) {

}
