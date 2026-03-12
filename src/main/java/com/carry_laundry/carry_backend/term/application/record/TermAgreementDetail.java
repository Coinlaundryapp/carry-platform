package com.carry_laundry.carry_backend.term.application.record;

import com.carry_laundry.carry_backend.term.domain.enums.TermType;

public record TermAgreementDetail(
    Long termId,
    String code,
    TermType termType,
    boolean agreeYn
) {

}
