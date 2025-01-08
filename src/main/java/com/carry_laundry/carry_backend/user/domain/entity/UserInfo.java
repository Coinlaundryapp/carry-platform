package com.carry_laundry.carry_backend.user.domain.entity;

import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount.Profile;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("user_infos")
public record UserInfo(
    @Id Long id,
    String thumbnailImageUrl,
    String profileImageUrl,
    LocalDateTime connectedAt,
    Boolean hasEmail,
    Boolean isEmailValid,
    Boolean isEmailVerified,
    String email,
    String ageRange,
    Boolean hasBirthday,
    String birthday,
    String birthdayType,
    String gender,
    String ci,
    LocalDateTime ciAuthenticatedAt
) {

    public static UserInfo from(KakaoOAuthResource resource) {
        KakaoAccount account = resource.getKakaoAccount();
        Profile profile = account.getProfile();
        return new UserInfo(
            resource.getId(),
            profile.getThumbnailImageUrl(),
            profile.getProfileImageUrl(),
            resource.getConnectedAt(),
            account.getHasEmail(),
            account.getIsEmailValid(),
            account.getIsEmailVerified(),
            account.getEmail(),
            account.getAgeRange(),
            account.getHasBirthday(),
            account.getBirthday(),
            account.getBirthdayType(),
            account.getGender(),
            account.getCi(),
            account.getCiAuthenticatedAt()
        );
    }

}
