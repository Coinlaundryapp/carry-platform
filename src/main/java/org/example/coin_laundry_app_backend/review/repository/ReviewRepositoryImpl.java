package org.example.coin_laundry_app_backend.review.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.domain.MediaRowConverter;
import org.example.coin_laundry_app_backend.review.presentation.payload.response.ReviewCommonResponse;
import org.example.coin_laundry_app_backend.review.presentation.payload.response.ReviewStatisticResponse;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final MediaRowConverter mediaRowConverter;

    @Override
    public Mono<ReviewStatisticResponse> getReviewStaticByLaundromatId(Long laundromatId) {
        String selectQuery = """
            SELECT COUNT(rating) AS review_count, AVG(rating) AS average_rating
            FROM reviews
            WHERE laundromat_id = :laundromatId
            """;
        return r2dbcEntityTemplate.getDatabaseClient().sql(selectQuery)
            .bind("laundromatId", laundromatId)
            .map((row, rowMetadata) -> {
                Long reviewCount = row.get("review_count", Long.class);
                Double averageRating = Optional.ofNullable(row.get("average_rating", Double.class))
                    .orElse(0.0);
                return new ReviewStatisticResponse(laundromatId, reviewCount, averageRating);
            }).one();
    }

    @Override
    public Flux<ReviewCommonResponse> getReviewsByLaundromatId(Long laundromatId) {
        String selectQuery = """
            WITH review_base AS (
                SELECT r.*
                FROM reviews r
                WHERE r.laundromat_id = :laundromatId
            ),
            review_user AS (
                SELECT u.id, u.nickname
                FROM users u
                JOIN review_base rb ON rb.user_id = u.id
            ),
            review_laundromat AS (
                SELECT l.id, l.name
                FROM laundromats l
                WHERE l.id = :laundromatId
            ),
            review_media_resource AS (
                SELECT rmr.review_id, array_agg(ROW(rmr.media_url, rmr.extension)) as media_resources
                FROM review_media_resources rmr
                WHERE rmr.review_id IN (SELECT id FROM review_base)
                GROUP BY rmr.review_id
            )
            SELECT rb.id, rb.rating, rb.comment, rb.created_at, rb.updated_at,
                   ru.nickname as username, rl.name as laundromat_name,
                   COALESCE(ri.media_resources, ARRAY[]::RECORD[]) as media_resources
            FROM review_base rb
            JOIN review_user ru ON rb.user_id = ru.id
            JOIN review_laundromat rl ON rb.laundromat_id = rl.id
            LEFT JOIN review_media_resource ri ON rb.id = ri.review_id
            """;
        return r2dbcEntityTemplate.getDatabaseClient().sql(selectQuery)
            .bind("laundromatId", laundromatId)
            .map((row, rowMetadata) -> ReviewCommonResponse.builder()
                .id(row.get("id", Long.class))
                .laundromatName(row.get("laundromat_name", String.class))
                .username(row.get("username", String.class))
                .mediaResources(
                    mediaRowConverter.readConvert(row.get("media_resources", String[].class)))
                .content(row.get("comment", String.class))
                .rating(row.get("rating", Integer.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .build()
            )
            .all();
    }
}
