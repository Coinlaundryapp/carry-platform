package com.carry_laundry.carry_backend.term.application.record;

import com.carry_laundry.carry_backend.term.domain.enums.TermType;
import java.time.LocalDate;
import org.springframework.lang.NonNull;

public record TermDetail(
    @NonNull Long id,
    @NonNull String code,
    @NonNull TermType termType,
    @NonNull Integer versionCount,
    @NonNull LocalDate createdAt
) {

    public String getVersion() {
        return String.format("%s_%d", createdAt, versionCount);
    }

    public boolean isMandatory() {
        return termType == TermType.MANDATORY;
    }
}
