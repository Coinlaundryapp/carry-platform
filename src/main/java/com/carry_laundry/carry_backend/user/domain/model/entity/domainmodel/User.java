package com.carry_laundry.carry_backend.user.domain.model.entity.domainmodel;

import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount.Profile;
import com.carry_laundry.carry_backend.user.domain.model.value.PhoneNumber;
import com.carry_laundry.carry_backend.user.domain.model.value.UserInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class User {

    private Long id;
    private final String name;
    private final String nickname;
    private final PhoneNumber phoneNumber;
    private UserInfo userInfo;

    public static User create(KakaoOAuthResource kakaoOAuthResource) {
        KakaoAccount kakaoAccount = kakaoOAuthResource.getKakaoAccount();
        Profile profile = kakaoAccount.getProfile();
        return new User(null, kakaoAccount.getName(), profile.getNickname(),
            PhoneNumber.from(kakaoAccount.getPhoneNumber()), UserInfo.from(kakaoOAuthResource));
    }
}