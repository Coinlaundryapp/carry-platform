package org.example.coin_laundry_app_backend.laundry.repository;

import org.example.coin_laundry_app_backend.laundry.domain.model.entity.Laundry;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LaundryRepository extends ReactiveCrudRepository<Laundry, Long> {

    @NonNull
    @Override
    @Query("INSERT INTO laundries (name, options, address, location_coordinate) VALUES (:#{#entity.name}, :#{#entity.options}::laundry_options[], :#{#entity.address}, ST_GeomFromText(:#{#entity.locationCoordinate}, 4326))")
    <S extends Laundry> Mono<S> save(@NonNull S entity);

    @Query(
        """
            SELECT *, st_dwithin(location_coordinate::geography, st_setsrid(st_makepoint(:latitude, :longitude), 4326), :distance) as dist
            FROM laundries WHERE st_dwithin(location_coordinate::geography, st_setsrid(st_makepoint(:latitude, :longitude), 4326), :distance)
            ORDER BY dist""")
    Flux<Laundry> findByLocationAndDistance(double latitude, double longitude, double distance);
}
