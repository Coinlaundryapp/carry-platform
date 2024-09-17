package org.example.coin_laundry_app_backend.review.repository;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.review.presentation.payload.response.ReviewMetadataResponse;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    @Override
    public Mono<ReviewMetadataResponse> getReviewMetadataByLaundryId(Long laundryId) {
        String selectQuery = """
            SELECT COUNT(rating) AS review_count, AVG(rating) AS average_rating
            FROM reviews
            WHERE laundry_id = :laundryId
            """;
        return r2dbcEntityTemplate.getDatabaseClient().sql(selectQuery)
            .bind("laundryId", laundryId)
            .map((row, rowMetadata) -> {
                Long reviewCount = row.get("review_count", Long.class);
                Double averageRating = Optional.ofNullable(row.get("average_rating", Double.class))
                    .orElse(0.0);
                return new ReviewMetadataResponse(laundryId, reviewCount, averageRating);
            }).one();
    }
}
