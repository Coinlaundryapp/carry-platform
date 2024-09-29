package com.carry_laundry.carry_backend.laundromat.domain.converter;

import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKBReader;

public class PointConverter {

    private final WKBReader wkbReader = new WKBReader();

    public Point readConvert(String source) {
        try {
            byte[] wkb = WKBReader.hexToBytes(source);
            return (Point) wkbReader.read(wkb);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String writeConvert(Point source) {
        return String.format("%f, %f", source.getX(), source.getY());
    }
}
