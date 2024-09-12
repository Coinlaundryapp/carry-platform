package com.carry_laundry.carry_backend.user.presentation.payload.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "약관 동의/철회 요청")
public class TermUpdateRequest {

    @Schema(description = "약관 ID", example = "1")
    private Long termId;
}
