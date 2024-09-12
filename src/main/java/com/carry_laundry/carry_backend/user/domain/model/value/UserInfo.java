package com.carry_laundry.carry_backend.user.domain.model.value;

import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount.Profile;
import com.carry_laundry.carry_backend.user.domain.model.entity.data.UserData;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserInfo {

    private final Long id;
    private String thumbnailImageUrl;
    private String profileImageUrl;
    private LocalDateTime connectedAt;
    private Boolean hasEmail;
    private Boolean isEmailValid;
    private Boolean isEmailVerified;
    private String email;
    private String ageRange;
    private Boolean hasBirthday;
    private String birthday;
    private String birthdayType;
    private String gender;
    private String ci;
    private LocalDateTime ciAuthenticatedAt;

    public static UserInfo from(KakaoOAuthResource response) {
        KakaoAccount kakaoAccount = response.getKakaoAccount();
        Profile profile = kakaoAccount.getProfile();
        return new UserInfo(response.getId(), profile.getThumbnailImageUrl(),
            profile.getProfileImageUrl(), response.getConnectedAt(), kakaoAccount.getHasEmail(),
            kakaoAccount.getIsEmailValid(), kakaoAccount.getIsEmailVerified(),
            kakaoAccount.getEmail(), kakaoAccount.getAgeRange(), kakaoAccount.getHasBirthday(),
            kakaoAccount.getBirthday(), kakaoAccount.getBirthdayType(), kakaoAccount.getGender(),
            kakaoAccount.getCi(), kakaoAccount.getCiAuthenticatedAt());
    }

    public static UserInfo from(UserData userData) {
        return new UserInfo(userData.getId(), userData.getThumbnailImageUrl(),
            userData.getProfileImageUrl(), userData.getConnectedAt(), userData.getHasEmail(),
            userData.getIsEmailValid(), userData.getIsEmailVerified(), userData.getEmail(),
            userData.getAgeRange(), userData.getHasBirthday(), userData.getBirthday(),
            userData.getBirthdayType(), userData.getGender(), userData.getCi(),
            userData.getCiAuthenticatedAt());
    }
}
