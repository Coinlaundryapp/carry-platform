package com.carry_laundry.carry_backend.user.application.service;

import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource;
import com.carry_laundry.carry_backend.user.domain.entity.User;
import com.carry_laundry.carry_backend.user.domain.entity.UserInfo;
import com.carry_laundry.carry_backend.user.repository.UserInfoRepository;
import com.carry_laundry.carry_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;

    public Mono<User> addUser(KakaoOAuthResource kakaoOAuthResource) {
        return userInfoRepository.save(UserInfo.from(kakaoOAuthResource))
            .then(userRepository.save(User.create(kakaoOAuthResource)));
    }

    public Mono<User> getUserByKakaoId(Long kakaoId) {
        return userRepository.findByUserInfoId(kakaoId);
    }

}
