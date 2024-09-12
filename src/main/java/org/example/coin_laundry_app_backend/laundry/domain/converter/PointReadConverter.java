package org.example.coin_laundry_app_backend.laundry.domain.converter;

import io.r2dbc.spi.Row;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKTReader;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class PointReadConverter implements Converter<Row, Point> {

    @Override
    public Point convert(Row source) {
        String value = source.get("location_coordinate", String.class);
        WKTReader wktReader = new WKTReader();
        try {
            return (Point) wktReader.read(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
