package org.example.coin_laundry_app_backend.laundry.domain.converter;

import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKBReader;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class PointReadConverter implements Converter<String, Point> {

    private final WKBReader wkbReader = new WKBReader();

    @Override
    public Point convert(@NonNull String source) {
        try {
            byte[] wkb = WKBReader.hexToBytes(source);
            return (Point) wkbReader.read(wkb);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
