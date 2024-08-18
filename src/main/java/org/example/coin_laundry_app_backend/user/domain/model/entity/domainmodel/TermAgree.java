package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.data.TermAgreeData;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TermAgree {

    private Long id;
    private final Long userId;
    private final Long termId;
    private Boolean agreeYn;
    private LocalDateTime updatedAt;

    public static TermAgree from(TermAgreeData termAgreeData) {
        return new TermAgree(termAgreeData.getId(), termAgreeData.getUserId(),
            termAgreeData.getTermId(), termAgreeData.getAgreeYn(), termAgreeData.getUpdatedAt());
    }

    public static TermAgree of(Long userId, Long termId, boolean agreeYn, LocalDateTime updatedAt) {
        return new TermAgree(null, userId, termId, agreeYn, updatedAt);
    }

    public void updateAgreeYn(boolean agreeYn, LocalDateTime updatedAt) {
        this.agreeYn = agreeYn;
        this.updatedAt = updatedAt;
    }

    public TermAgreeData toData() {
        return new TermAgreeData(id, userId, termId, agreeYn, updatedAt);
    }
}
