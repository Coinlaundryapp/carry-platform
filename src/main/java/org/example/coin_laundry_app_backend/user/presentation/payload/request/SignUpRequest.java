package org.example.coin_laundry_app_backend.user.presentation.payload.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {

    private String phoneNumber;
    private Boolean commercialYn;
    private Boolean locationYn;
}
