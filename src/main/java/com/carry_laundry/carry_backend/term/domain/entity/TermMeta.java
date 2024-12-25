package com.carry_laundry.carry_backend.term.domain.entity;

import com.carry_laundry.carry_backend.term.domain.enums.TermType;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 약관 메타 정보를 나타내는 엔티티입니다.
 */
@Getter
@Table("term_metas")
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TermMeta {

    /**
     * 약관 고유 식별자
     */
    @Id
    private Long id;
    /**
     * 약관 제목을 나타냅니다. ex) 이용약관, 개인정보처리방침
     */
    private String title;
    /**
     * 약관 코드를 나타냅니다. ex) LOCATION, USER_PRIVACY
     */
    private String code;
    /**
     * 약관 유형을 나타냅니다. ex) MANDATORY, OPTIONAL
     */
    private TermType termType;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    public static TermMeta of(String title, String code,
        TermType termType) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title should not be blank");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code should not be blank");
        }
        if (termType == null) {
            throw new IllegalArgumentException("termType should not be null");
        }
        return new TermMeta(null, title, code, termType, null, null);
    }

    public void updateValues(String title, String code, TermType termType) {
        if ((title == null || title.isBlank()) && (code == null || code.isBlank())
            && termType == null) {
            throw new IllegalArgumentException("At least one parameter should be not null");
        }
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (code != null && !code.isBlank()) {
            this.code = code;
        }
        if (termType != null) {
            this.termType = termType;
        }
    }
}
