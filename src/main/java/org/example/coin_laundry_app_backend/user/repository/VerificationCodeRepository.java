package org.example.coin_laundry_app_backend.user.repository;

import org.example.coin_laundry_app_backend.user.domain.model.entity.data.VerificationCodeData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface VerificationCodeRepository extends
    ReactiveCrudRepository<VerificationCodeData, Long> {

    Mono<VerificationCodeData> findByPhoneNumberAndCode(String phoneNumber, String code);

}
