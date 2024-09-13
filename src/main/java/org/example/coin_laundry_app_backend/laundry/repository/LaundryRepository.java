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
    @Query("INSERT INTO laundries (name, address, location_coordinate) VALUES (:#{#entity.name}, :#{#entity.address}, ST_MakePoint(:#{#entity.locationCoordinate.x}, :#{#entity.locationCoordinate.y})::geography) RETURNING *")
    <S extends Laundry> Mono<S> save(@NonNull S entity);

    @Query(
        """
            SELECT *, ST_DWithin(location_coordinate, ST_MakePoint(:longitude, :latitude)::geography, :distance) as dist
            FROM laundries
            WHERE ST_DWithin(location_coordinate, ST_MakePoint(:longitude, :latitude)::geography, :distance)
            ORDER BY dist""")
    Flux<Laundry> findByLocationAndDistance(double latitude, double longitude, double distance);
}
