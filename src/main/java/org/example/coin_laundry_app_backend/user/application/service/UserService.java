package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.converter.UserConverter;
import org.example.coin_laundry_app_backend.user.domain.entity.domainmodel.User;
import org.example.coin_laundry_app_backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;

    public Mono<User> addUser(User user) {
        return userRepository.save(UserConverter.toData(user)).map(UserConverter::toDomain);
    }

    public Mono<User> getUserById(Long id) {
        return userRepository.findById(id).map(UserConverter::toDomain);
    }

    public Mono<User> getUserByKakaoId(Long kakaoId) {
        return userRepository.findByKakaoId(kakaoId).map(UserConverter::toDomain);
    }

    public Flux<User> getAllUsers() {
        return userRepository.findAll()
            .map(UserConverter::toDomain);
    }
}
