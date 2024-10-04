package com.carry_laundry.carry_backend.user.presentation.payload.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VerificationCodeRequest {

    private String phoneNumber;
}
