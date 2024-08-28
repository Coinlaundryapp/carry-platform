package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.value.PhoneNumber;
import org.example.coin_laundry_app_backend.user.domain.model.value.UserInfo;
import org.example.coin_laundry_app_backend.user.application.record.oauth.KakaoOAuthResource;
import org.example.coin_laundry_app_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount;
import org.example.coin_laundry_app_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount.Profile;

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