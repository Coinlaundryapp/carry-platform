package com.carry_laundry.carry_backend.laundromat.repository;

import com.carry_laundry.carry_backend.common.domain.MediaRowConverter;
import com.carry_laundry.carry_backend.laundromat.domain.converter.LaundromatOptionsConverter;
import com.carry_laundry.carry_backend.laundromat.domain.converter.PointConverter;
import com.carry_laundry.carry_backend.laundromat.domain.entity.Laundromat;
import com.carry_laundry.carry_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.lang.NonNull;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class LaundromatRepositoryImpl implements LaundromatRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final MediaRowConverter mediaRowConverter;
    private final PointConverter pointConverter = new PointConverter();
    private final LaundromatOptionsConverter laundromatOptionsConverter = new LaundromatOptionsConverter();

    @Override
    public Flux<LaundromatCommonResponse> findByLocationAndDistance(double latitude,
        double longitude,
        double distance) {
        String selectSQL = """
            WITH laundromat_base AS (
                SELECT l.*, ST_Distance(l.location_coordinate, ST_MakePoint(:longitude, :latitude)::geography) as distance
                FROM laundromats l
                WHERE ST_DWithin(l.location_coordinate, ST_MakePoint(:longitude, :latitude)::geography, :dist)
                ),
                 laundromat_option_values AS (
                     SELECT lom.laundromat_id, array_agg(lom.laundromat_option) as options
                     FROM laundromat_option_mappings lom
                              JOIN laundromat_base lb ON lb.id = lom.laundromat_id
                     GROUP BY lom.laundromat_id
                 ),
                 laundromat_media_resource_values AS (
                     SELECT lmr.laundromat_id, array_agg(row(lmr.media_url, lmr.extension)) as media_resources
                     FROM laundromat_media_resources lmr
                              JOIN laundromat_base lb ON lb.id = lmr.laundromat_id
                     GROUP BY lmr.laundromat_id
                 )
            SELECT lb.*, COALESCE(lov.options, ARRAY[]::laundromat_options[]) as options, COALESCE(lmrv.media_resources, ARRAY[]::record[]) as media_resources
            FROM laundromat_base lb
                     LEFT JOIN laundromat_option_values lov ON lb.id = lov.laundromat_id
                     LEFT JOIN laundromat_media_resource_values lmrv ON lb.id = lmrv.laundromat_id
            ORDER BY lb.distance
            """;
        GenericExecuteSpec spec = r2dbcEntityTemplate.getDatabaseClient().sql(selectSQL)
            .bind("latitude", latitude)
            .bind("longitude", longitude)
            .bind("dist", distance);

        return spec.map((row, rowMetadata) -> {
            Point locationCoordinate = pointConverter.readConvert(
                row.get("location_coordinate", String.class));
            return LaundromatCommonResponse.builder()
                .id(row.get("id", Long.class))
                .name(row.get("name", String.class))
                .address(row.get("address", String.class))
                .latitude(locationCoordinate.getY())
                .longitude(locationCoordinate.getX())
                .distance(row.get("distance", Double.class))
                .options(laundromatOptionsConverter.readCovert(row.get("options", String.class)))
                .mediaResources(
                    mediaRowConverter.readConvert(row.get("media_resources", String[].class)))
                .build();
        }).all();
    }

    @Override
    public Mono<Laundromat> findById(@NonNull Long id) {
        String selectQuery = "SELECT id, name, address, location_coordinate, created_at, updated_at FROM laundromats WHERE id = :id";
        GenericExecuteSpec spec = r2dbcEntityTemplate.getDatabaseClient().sql(selectQuery)
            .bind("id", id);
        return spec.map((row, rowMetadata) -> {
            Point locationCoordinate = pointConverter.readConvert(
                row.get("location_coordinate", String.class));
            return new Laundromat(row.get("id", Long.class),
                row.get("name", String.class),
                row.get("address", String.class),
                locationCoordinate,
                row.get("created_at", LocalDateTime.class),
                row.get("updated_at", LocalDateTime.class));
        }).one();
    }
}
