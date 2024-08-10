package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTTokenResponse;
import org.example.coin_laundry_app_backend.user.domain.model.entity.data.RefreshTokenData;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken {

    private final Long id;
    private final String value;
    private final LocalDateTime expiryAt;

    public static RefreshToken from(RefreshTokenData tokenData) {
        return new RefreshToken(tokenData.getId(), tokenData.getValue(), tokenData.getExpiryAt());
    }

    public static RefreshToken from(JWTTokenResponse response) {
        return new RefreshToken(null, response.getRefreshToken(),
            response.getRefreshTokenExpiryAt());
    }

    public RefreshTokenData toData() {
        return new RefreshTokenData(id, value, expiryAt);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryAt);
    }

}
