package org.example.coin_laundry_app_backend.user.presentation.payload.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.User;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserTermsResponse {

    private Boolean commercialYn;
    private Boolean locationYn;

    public static UserTermsResponse from(User user) {
        return new UserTermsResponse(user.getCommercialYn(), user.getLocationYn());
    }
}
