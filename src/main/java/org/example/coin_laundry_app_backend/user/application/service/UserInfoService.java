package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.User;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.UserTermsResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserInfoService {

    private final UserService userService;

    public Mono<UserTermsResponse> getUserTermsInfo(Long userId) {
        Mono<User> userMono = userService.getUserById(userId);
        return userMono.flatMap(user -> Mono.just(UserTermsResponse.from(user)));
    }

    public Mono<Void> setCommercialTermYn(Long userId, Boolean commercialYn) {
        Mono<User> userMono = userService.getUserById(userId);
        return userMono.flatMap(
            user -> {
                validateCommercialTerm(user, commercialYn);
                user.setCommercialYn(commercialYn);
                return userService.addUser(user).flatMap(user1 -> Mono.empty());
            }
        );
    }

    public Mono<Void> setLocationTermYn(Long userId, Boolean locationYn) {
        Mono<User> userMono = userService.getUserById(userId);
        return userMono.flatMap(
            user -> {
                validateLocationTerm(user, locationYn);
                user.setLocationYn(locationYn);
                return userService.addUser(user).flatMap(user1 -> Mono.empty());
            }
        );
    }

    private void validateCommercialTerm(User user, boolean commercialYn) {
        if (user.getCommercialYn().equals(commercialYn)) {
            throw new IllegalArgumentException(
                "이미 광고성 정보 수신에 " + (commercialYn ? "동의" : "철회") + " 했습니다.");
        }
    }

    private void validateLocationTerm(User user, boolean locationYn) {
        if (user.getLocationYn().equals(locationYn)) {
            throw new IllegalArgumentException(
                "이미 위치 정보 제공에 " + (locationYn ? "동의" : "철회") + " 했습니다.");
        }
    }
}
