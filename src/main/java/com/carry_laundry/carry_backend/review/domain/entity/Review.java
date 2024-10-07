package com.carry_laundry.carry_backend.review.domain.entity;

import com.carry_laundry.carry_backend.review.domain.enums.ReviewRating;
import io.r2dbc.spi.Row;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("reviews")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Review {

    @Id
    private Long id;
    private Long laundromatId;
    private Long userId;
    private String comment;
    private ReviewRating reviewRating;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    private Review(Long laundromatId, Long userId, String comment, Integer reviewRating) {
        this.laundromatId = laundromatId;
        this.userId = userId;
        this.comment = comment;
        this.reviewRating = ReviewRating.from(reviewRating);
    }

    public static Review fromRow(Row row) {
        return new Review(
            row.get("id", Long.class),
            row.get("laundromat_id", Long.class),
            row.get("user_id", Long.class),
            row.get("comment", String.class),
            ReviewRating.from(row.get("rating", Integer.class)),
            row.get("created_at", LocalDateTime.class),
            row.get("updated_at", LocalDateTime.class)
        );
    }

}
