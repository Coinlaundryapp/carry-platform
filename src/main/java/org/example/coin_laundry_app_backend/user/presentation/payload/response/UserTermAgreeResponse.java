package org.example.coin_laundry_app_backend.user.presentation.payload.response;

import java.time.LocalDateTime;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.TermAgree;

@Getter
public class UserTermAgreeResponse {

    private final Long id;
    private final Long userId;
    private final Long termId;
    private final Boolean agreeYn;
    private final LocalDateTime updatedAt;

    private UserTermAgreeResponse(Long id, Long userId, Long termId, Boolean agreeYn,
        LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.termId = termId;
        this.agreeYn = agreeYn;
        this.updatedAt = updatedAt;
    }

    public static UserTermAgreeResponse from(TermAgree termAgree) {
        return new UserTermAgreeResponse(termAgree.getId(), termAgree.getUserId(),
            termAgree.getTermId(), termAgree.getAgreeYn(), termAgree.getUpdatedAt());
    }
}
