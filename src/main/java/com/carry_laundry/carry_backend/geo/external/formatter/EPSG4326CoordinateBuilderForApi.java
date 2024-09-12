package com.carry_laundry.carry_backend.geo.external.formatter;

import com.carry_laundry.carry_backend.geo.domain.model.EPSG4326Coordinate;

public class EPSG4326CoordinateBuilderForApi {
    public static String build(EPSG4326Coordinate coordinate) {
        return String.format("%f,%f", coordinate.longitude(), coordinate.latitude());
    }
}
