package com.carry_laundry.carry_backend.user.presentation.payload.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {

    private String phoneNumber;
    private Boolean commercialYn;
    private Boolean locationYn;
}
