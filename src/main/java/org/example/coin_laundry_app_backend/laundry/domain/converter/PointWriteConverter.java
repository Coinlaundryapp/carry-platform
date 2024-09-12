package org.example.coin_laundry_app_backend.laundry.domain.converter;

import org.locationtech.jts.geom.Point;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public class PointWriteConverter implements Converter<Point, String> {

    @Override
    public String convert(Point point) {
        return String.format("POINT(%f %f)", point.getX(), point.getY());
    }
}
