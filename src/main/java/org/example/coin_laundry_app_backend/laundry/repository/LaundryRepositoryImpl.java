package org.example.coin_laundry_app_backend.laundry.repository;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.laundry.domain.converter.LaundryOptionsConverter;
import org.example.coin_laundry_app_backend.laundry.domain.converter.PointConverter;
import org.example.coin_laundry_app_backend.laundry.presentation.payload.response.LaundryCommonResponse;
import org.locationtech.jts.geom.Point;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class LaundryRepositoryImpl implements LaundryRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final PointConverter pointConverter = new PointConverter();
    private final LaundryOptionsConverter laundryOptionsConverter = new LaundryOptionsConverter();

    @Override
    public Flux<LaundryCommonResponse> findByLocationAndDistance(double latitude, double longitude,
        double distance) {
        String selectSQL = """
            WITH laundry_base AS (
                SELECT l.*, ST_Distance(l.location_coordinate, ST_MakePoint(:longitude, :latitude)::geography) as distance
                FROM laundries l
                WHERE ST_DWithin(l.location_coordinate, ST_MakePoint(:longitude, :latitude)::geography, :dist)
            ),
            laundry_option_values AS (
                SELECT lom.laundry_id, array_agg(lom.laundry_option) as options
                FROM laundry_option_mappings lom
                JOIN laundry_base lb ON lb.id = lom.laundry_id
                GROUP BY lom.laundry_id
            ),
            laundry_image_values AS (
                SELECT li.laundry_id, array_agg(li.image_url) as image_urls
                FROM laundry_images li
                JOIN laundry_base lb ON lb.id = li.laundry_id
                GROUP BY li.laundry_id
            )
            SELECT lb.*, COALESCE(lov.options, ARRAY[]::laundry_options[]) as options, COALESCE(liv.image_urls, ARRAY[]::varchar[]) as image_urls
            FROM laundry_base lb
            LEFT JOIN laundry_option_values lov ON lb.id = lov.laundry_id
            LEFT JOIN laundry_image_values liv ON lb.id = liv.laundry_id
            ORDER BY lb.distance
            """;
        GenericExecuteSpec spec = r2dbcEntityTemplate.getDatabaseClient().sql(selectSQL)
            .bind("latitude", latitude)
            .bind("longitude", longitude)
            .bind("dist", distance);

        return spec.map((row, rowMetadata) -> {
            Point locationCoordinate = pointConverter.readConvert(
                row.get("location_coordinate", String.class));
            return LaundryCommonResponse.builder()
                .id(row.get("id", Long.class))
                .name(row.get("name", String.class))
                .address(row.get("address", String.class))
                .latitude(locationCoordinate.getY())
                .longitude(locationCoordinate.getX())
                .distance(row.get("distance", Double.class))
                .options(laundryOptionsConverter.readCovert(row.get("options", String.class)))
                .imageUrls(Arrays.asList(row.get("image_urls", String[].class)))
                .build();
        }).all();
    }
}
