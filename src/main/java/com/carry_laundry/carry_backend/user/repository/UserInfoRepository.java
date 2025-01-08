package com.carry_laundry.carry_backend.user.repository;

import com.carry_laundry.carry_backend.user.domain.entity.UserInfo;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserInfoRepository extends ReactiveCrudRepository<UserInfo, Long> {

    @Override
    @NonNull
    @Query("""
        INSERT INTO user_infos (id, thumbnail_image_url, profile_image_url, connected_at, has_email, is_email_valid, is_email_verified, email, age_range, has_birthday, birthday, birthday_type, gender, ci, ci_authenticated_at)
        VALUES (:#{#entity.id}, :#{#entity.thumbnailImageUrl}, :#{#entity.profileImageUrl}, :#{#entity.connectedAt}, :#{#entity.hasEmail}, :#{#entity.isEmailValid}, :#{#entity.isEmailVerified}, :#{#entity.email}, :#{#entity.ageRange}, :#{#entity.hasBirthday}, :#{#entity.birthday}, :#{#entity.birthdayType}, :#{#entity.gender}, :#{#entity.ci}, :#{#entity.ciAuthenticatedAt})
        ON CONFLICT (id)
        DO UPDATE SET
            thumbnail_image_url = EXCLUDED.thumbnail_image_url,
            profile_image_url = EXCLUDED.profile_image_url,
            connected_at = EXCLUDED.connected_at,
            has_email = EXCLUDED.has_email,
            is_email_valid = EXCLUDED.is_email_valid,
            is_email_verified = EXCLUDED.is_email_verified,
            email = EXCLUDED.email,
            age_range = EXCLUDED.age_range,
            has_birthday = EXCLUDED.has_birthday,
            birthday = EXCLUDED.birthday,
            birthday_type = EXCLUDED.birthday_type,
            gender = EXCLUDED.gender,
            ci = EXCLUDED.ci,
            ci_authenticated_at = EXCLUDED.ci_authenticated_at
        RETURNING *
        """)
    <S extends UserInfo> Mono<S> save(@NonNull S entity);
}
