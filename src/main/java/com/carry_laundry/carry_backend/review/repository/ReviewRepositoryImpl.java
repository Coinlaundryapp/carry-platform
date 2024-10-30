package com.carry_laundry.carry_backend.review.repository;

import com.carry_laundry.carry_backend.common.domain.MediaRowConverter;
import com.carry_laundry.carry_backend.review.application.record.ReviewDetailData;
import com.carry_laundry.carry_backend.review.domain.entity.Review;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewCommonResponse;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewStatisticResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.lang.NonNull;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final MediaRowConverter mediaRowConverter;

    @Override
    public Mono<Review> save(@NonNull Review review) {
        return r2dbcEntityTemplate.getDatabaseClient().sql(INSERT_REVIEW_QUERY)
            .bind(LAUNDROMAT_ID, review.getLaundromatId())
            .bind(USER_ID, review.getUserId())
            .bind(COMMENT, review.getComment())
            .bind(RATING, review.getReviewRating().getValue())
            .map((row, rowMetadata) -> Review.fromRow(row))
            .one();
    }

    @Override
    public Mono<ReviewStatisticResponse> getReviewStaticByLaundromatId(Long laundromatId) {
        return r2dbcEntityTemplate.getDatabaseClient().sql(SELECT_REVIEW_STATS_QUERY)
            .bind(LAUNDROMAT_ID, laundromatId)
            .map((row, rowMetadata) -> {
                Long reviewCount = row.get(REVIEW_COUNT, Long.class);
                Double averageRating = Optional.ofNullable(row.get(AVERAGE_RATING, Double.class))
                    .orElse(0.0);
                return new ReviewStatisticResponse(laundromatId, reviewCount, averageRating);
            })
            .one();
    }

    @Override
    public Flux<ReviewCommonResponse> getReviewsByLaundromatId(@NonNull Long laundromatId,
        @NonNull Long reviewId, @NonNull Integer size) {
        String selectQuery = generateSelectQuery(reviewId);
        GenericExecuteSpec executeSpec = r2dbcEntityTemplate.getDatabaseClient()
            .sql(selectQuery)
            .bind(LAUNDROMAT_ID, laundromatId)
            .bind(SIZE, size + 1);
        if (reviewId > 0) {
            executeSpec = executeSpec.bind(REVIEW_ID, reviewId);
        }
        return executeSpec.map((row, rowMetadata) -> ReviewCommonResponse.builder()
                .id(row.get(ID, Long.class))
                .laundromatId(row.get(LAUNDROMAT_ID, Long.class))
                .laundromatName(row.get(LAUNDROMAT_NAME, String.class))
                .userId(row.get(USER_ID, Long.class))
                .username(row.get(USERNAME, String.class))
                .mediaResources(
                    mediaRowConverter.convertToCommonResponse(
                        row.get(REVIEW_MEDIA_RESOURCES, String[].class)))
                .content(row.get(COMMENT, String.class))
                .rating(row.get(RATING, Integer.class))
                .createdAt(row.get(CREATED_AT, LocalDateTime.class))
                .updatedAt(row.get(UPDATED_AT, LocalDateTime.class))
                .build()
            )
            .all();
    }

    @Override
    public Mono<ReviewDetailData> findDetailDataById(Long reviewId) {
        return r2dbcEntityTemplate.getDatabaseClient().sql(SELECT_DETAIL_DATA_QUERY)
            .bind(REVIEW_ID, reviewId)
            .map((row, rowMetadata) ->
                new ReviewDetailData(
                    row.get(ID, Long.class),
                    row.get(LAUNDROMAT_ID, Long.class),
                    row.get(USER_ID, Long.class),
                    row.get(COMMENT, String.class),
                    row.get(RATING, Integer.class),
                    mediaRowConverter.convertToReviewMediaResource(
                        row.get(REVIEW_MEDIA_RESOURCES, String.class)),
                    row.get(CREATED_AT, LocalDateTime.class),
                    row.get(UPDATED_AT, LocalDateTime.class)
                )
            )
            .one();
    }

    @Override
    public Mono<Void> deleteById(Long reviewId) {
        return r2dbcEntityTemplate.getDatabaseClient().sql(DELETE_REVIEW_QUERY)
            .bind(REVIEW_ID, reviewId)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private String generateSelectQuery(Long reviewId) {
        StringBuilder queryBuilder = new StringBuilder("""
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
                SELECT rmr.review_id, array_agg(ROW(rmr.media_uri, rmr.extension)) as media_resources
                FROM review_media_resources rmr
                WHERE rmr.review_id IN (SELECT id FROM review_base)
                GROUP BY rmr.review_id
            )
            SELECT DISTINCT rb.id, rb.rating, rb.comment, rb.created_at, rb.updated_at,
                   ru.id as user_id, rl.id as laundromat_id,
                   ru.nickname as username, rl.name as laundromat_name,
                   COALESCE(ri.media_resources, ARRAY[]::RECORD[]) as media_resources
            FROM review_base rb
            JOIN review_user ru ON rb.user_id = ru.id
            JOIN review_laundromat rl ON rb.laundromat_id = rl.id
            LEFT JOIN review_media_resource ri ON rb.id = ri.review_id
            """);
        if (reviewId > 0) {
            queryBuilder.append(" WHERE rb.id < :reviewId");
        }
        queryBuilder.append(" ORDER BY rb.id DESC LIMIT :size");
        return queryBuilder.toString();
    }

    private static final String INSERT_REVIEW_QUERY = """
        INSERT INTO reviews (laundromat_id, user_id, comment, rating)
        VALUES (:laundromatId, :userId, :comment, :rating)
        RETURNING *
        """;

    private static final String SELECT_REVIEW_STATS_QUERY = """
        SELECT COUNT(rating) AS review_count, AVG(rating) AS average_rating
        FROM reviews
        WHERE laundromat_id = :laundromatId
        """;
    private static final String DELETE_REVIEW_QUERY = """
        DELETE FROM reviews
        WHERE id = :reviewId
        """;

    private static final String SELECT_DETAIL_DATA_QUERY = """
        SELECT r.*,
               json_agg(to_json(rmr)) AS media_resources
        FROM reviews r
                 LEFT JOIN review_media_resources rmr ON r.id = rmr.review_id
        WHERE r.id = :reviewId
        GROUP BY r.id
        """;

    private static final String LAUNDROMAT_ID = "laundromatId";
    private static final String USER_ID = "userId";
    private static final String COMMENT = "comment";
    private static final String RATING = "rating";
    private static final String REVIEW_COUNT = "review_count";
    private static final String AVERAGE_RATING = "average_rating";
    private static final String SIZE = "size";
    private static final String REVIEW_ID = "reviewId";
    private static final String USERNAME = "username";
    private static final String LAUNDROMAT_NAME = "laundromat_name";
    private static final String ID = "id";
    private static final String REVIEW_MEDIA_RESOURCES = "media_resources";
    private static final String CREATED_AT = "created_at";
    private static final String UPDATED_AT = "updated_at";
}
