package com.carry_laundry.carry_backend.term.domain.entity;

import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("terms")
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Term {

    @Id
    private Long id;
    private Long termMetaId;
    private String content;
    private Integer versionCount;
    @CreatedDate
    private LocalDate createdAt;

    public static Term of(long termMetaId, String content, int versionCount, LocalDate createdAt) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content should not be blank");
        }
        if (versionCount < 1) {
            throw new IllegalArgumentException("versionCount should be greater than 0");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt should not be null");
        }
        return new Term(null, termMetaId, content, versionCount, createdAt);
    }

    public String getVersion() {
        return String.format("%s_%d", createdAt, versionCount);
    }

}
