package com.carry_laundry.carry_backend.user.application.service;

import com.carry_laundry.carry_backend.user.domain.entity.RefreshToken;
import com.carry_laundry.carry_backend.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    public Mono<RefreshToken> addRefreshToken(RefreshToken refreshToken) {
        return refreshTokenRepository.save(refreshToken);
    }

    public Mono<RefreshToken> findRefreshTokenByValue(String value) {
        return refreshTokenRepository.findByValue(value)
            .flatMap(refreshToken -> {
                if (refreshToken.isExpired()) {
                    return refreshTokenRepository.delete(refreshToken).then(Mono.empty());
                }
                return Mono.just(refreshToken);
            });
    }
}
