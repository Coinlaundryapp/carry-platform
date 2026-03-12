package com.carry_laundry.carry_backend.term.presentation.payload.response;

import com.carry_laundry.carry_backend.term.domain.enums.TermType;
import java.time.LocalDate;

public record TermCommonResponse(
    Long id,
    String code,
    String title,
    String content,
    TermType termType,
    Integer versionCount,
    LocalDate createdAt
) {

}
