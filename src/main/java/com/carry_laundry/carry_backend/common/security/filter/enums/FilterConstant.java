package com.carry_laundry.carry_backend.common.security.filter.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FilterConstant {
    TOKEN_PREFIX("Bearer "),
    TOKEN_DETAIL("TOKEN_DETAIL"),
    HEADER_X_USER_ID("X-USER-ID"),
    EXCEPTION("EXCEPTION");

    private final String value;
}
