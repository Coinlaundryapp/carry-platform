package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.data.UserData;
import org.example.coin_laundry_app_backend.user.domain.model.value.PhoneNumber;
import org.example.coin_laundry_app_backend.user.domain.model.value.UserInfo;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUserResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUserResponse.KakaoAccount;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.KakaoUserResponse.KakaoAccount.Profile;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    private Long id;
    private final String name;
    private final String nickname;
    private final PhoneNumber phoneNumber;
    private UserInfo userInfo;

    public static User from(KakaoUserResponse kakaoUserResponse) {
        KakaoAccount kakaoAccount = kakaoUserResponse.getKakaoAccount();
        Profile profile = kakaoAccount.getProfile();
        return new User(null, kakaoAccount.getName(), profile.getNickname(),
            PhoneNumber.from(kakaoAccount.getPhoneNumber()), UserInfo.from(kakaoUserResponse));
    }

    public static User from(UserData userData) {
        return new User(userData.getId(), userData.getName(), userData.getNickname(),
            PhoneNumber.from(userData.getPhoneNumber()), UserInfo.from(userData));
    }

    public UserData toData() {
        return new UserData(id, name, nickname, phoneNumber.toString(), userInfo.getId(),
            userInfo.getThumbnailImageUrl(), userInfo.getProfileImageUrl(),
            userInfo.getConnectedAt(), userInfo.getHasEmail(), userInfo.getIsEmailValid(),
            userInfo.getIsEmailVerified(), userInfo.getEmail(), userInfo.getAgeRange(),
            userInfo.getHasBirthday(), userInfo.getBirthday(), userInfo.getBirthdayType(),
            userInfo.getGender(), userInfo.getCi(), userInfo.getCiAuthenticatedAt());
    }

}
