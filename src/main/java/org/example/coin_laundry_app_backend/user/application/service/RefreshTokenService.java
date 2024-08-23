package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.RefreshToken;
import org.example.coin_laundry_app_backend.user.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    public Mono<RefreshToken> addRefreshToken(RefreshToken refreshToken) {
        return refreshTokenRepository.save(refreshToken.toData()).map(RefreshToken::from);
    }

    public Mono<RefreshToken> findRefreshTokenByValue(String value) {
        return refreshTokenRepository.findByValue(value).map(RefreshToken::from)
            .flatMap(refreshToken -> {
                if (refreshToken.isExpired()) {
                    return refreshTokenRepository.delete(refreshToken.toData()).then(Mono.empty());
                }
                return Mono.just(refreshToken);
            });
    }
}
