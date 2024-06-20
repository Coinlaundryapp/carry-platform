package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.User;
import org.example.coin_laundry_app_backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;

    public Flux<User> getAllUsers() {
        return userRepository.findAll()
                .map(user -> new User(user.getId(), user.getName()));
    }
}
