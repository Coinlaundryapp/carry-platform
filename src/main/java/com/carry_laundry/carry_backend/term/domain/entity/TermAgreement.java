package com.carry_laundry.carry_backend.term.domain.entity;

import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.NonNull;

@Getter
@Table("term_agreements")
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TermAgreement {

    @Id
    private Long id;
    private Long userId;
    private Long termId;
    private Boolean agreeYn;
    @LastModifiedDate
    private LocalDate updatedAt;

    public static TermAgreement of(@NonNull Long userId, @NonNull Long termId, boolean agreeYn) {
        return new TermAgreement(null, userId, termId, agreeYn, LocalDate.now());
    }

    public void updateAgreeYn(boolean agreeYn) {
        if (this.agreeYn.equals(agreeYn)) {
            throw new IllegalArgumentException("이미 " + (agreeYn ? "동의" : "비동의") + "한 상태입니다.");
        }
        this.agreeYn = agreeYn;
        this.updatedAt = LocalDate.now();
    }

}
