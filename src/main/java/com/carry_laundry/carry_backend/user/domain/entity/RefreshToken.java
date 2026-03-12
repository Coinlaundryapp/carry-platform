package com.carry_laundry.carry_backend.user.domain.entity;

import com.carry_laundry.carry_backend.common.security.payload.JWTTokenResponse;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("refresh_tokens")
public record RefreshToken(
    @Id Long id,
    Long userId,
    String value,
    LocalDateTime expiryAt
) {

    public static RefreshToken of(Long userId, JWTTokenResponse response) {
        return new RefreshToken(null, userId, response.getRefreshToken(),
            response.getRefreshTokenExpiryAt());
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryAt);
    }

}
