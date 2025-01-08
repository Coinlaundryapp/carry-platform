package com.carry_laundry.carry_backend.user.repository;

import com.carry_laundry.carry_backend.user.domain.entity.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    Mono<User> findByUserInfoId(Long userInfoId);
}