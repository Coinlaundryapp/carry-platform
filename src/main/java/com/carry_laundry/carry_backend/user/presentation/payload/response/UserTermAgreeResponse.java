package com.carry_laundry.carry_backend.user.presentation.payload.response;

import com.carry_laundry.carry_backend.user.domain.entity.domainmodel.TermAgree;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Schema(description = "사용자 약관 동의 정보 응답")
public class UserTermAgreeResponse {

    @Schema(description = "약관 동의 정보의 고유 ID", example = "1")
    private final Long id;

    @Schema(description = "사용자 ID", example = "1")
    private final Long userId;

    @Schema(description = "약관 ID", example = "1")
    private final Long termId;

    @Schema(description = "동의 여부", example = "true")
    private final Boolean agreeYn;

    @Schema(description = "마지막 업데이트 일시", example = "2023-08-16T14:30:00")
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
