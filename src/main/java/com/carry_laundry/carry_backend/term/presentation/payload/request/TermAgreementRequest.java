package com.carry_laundry.carry_backend.term.presentation.payload.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TermAgreementRequest(
    @NotNull(message = "약관 ID는 필수값입니다.") @Schema(description = "약관 ID", example = "1") Long termId,
    @NotNull(message = "동의/비동의 여부는 필수값입니다.") @Schema(description = "동의 여부", example = "true") boolean agreeYn
) {

}
