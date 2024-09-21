package org.example.coin_laundry_app_backend.review.repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
            review_laundry AS (
                SELECT l.id, l.name
                FROM laundromats l
                WHERE l.id = :laundryId
            ),
            review_image AS (
                SELECT ri.review_id, array_agg(ri.image_url) as image_urls
                FROM review_images ri
                WHERE ri.review_id IN (SELECT id FROM review_base)
                GROUP BY ri.review_id
            )
            SELECT rb.id, rb.rating, rb.comment, rb.created_at, rb.updated_at,\s
                   ru.nickname as username, rl.name as laundry_name,\s
                   COALESCE(ri.image_urls, ARRAY[]::VARCHAR[]) as image_urls
            FROM review_base rb
            JOIN review_user ru ON rb.user_id = ru.id
            JOIN review_laundry rl ON rb.laundromat_id = rl.id
            LEFT JOIN review_image ri ON rb.id = ri.review_id
            """;
        return r2dbcEntityTemplate.getDatabaseClient().sql(selectQuery)
            .bind("laundromatId", laundromatId)
            .map((row, rowMetadata) -> new ReviewCommonResponse(
                row.get("id", Long.class),
                row.get("laundry_name", String.class),
                row.get("username", String.class),
                Arrays.asList(row.get("image_urls", String[].class)),
                row.get("comment", String.class),
                row.get("rating", Integer.class),
                row.get("created_at", LocalDateTime.class).toString(),
                row.get("updated_at", LocalDateTime.class).toString()
            ))
            .all();
    }
}
