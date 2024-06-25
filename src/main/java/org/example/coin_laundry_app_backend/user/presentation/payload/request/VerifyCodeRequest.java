package org.example.coin_laundry_app_backend.user.presentation.payload.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VerifyCodeRequest {

    private String phoneNumber;
    private String verificationCode;
}
