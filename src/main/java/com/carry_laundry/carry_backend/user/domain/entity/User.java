package com.carry_laundry.carry_backend.user.domain.entity;

import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount;
import com.carry_laundry.carry_backend.user.application.record.oauth.KakaoOAuthResource.KakaoAccount.Profile;
import com.carry_laundry.carry_backend.user.domain.value.PhoneNumber;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("users")
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private Long id;
    private String name;
    private String nickname;
    private PhoneNumber phoneNumber;
    @Column("kakao_id")
    private Long userInfoId;

    public static User create(KakaoOAuthResource resource) {
        KakaoAccount account = resource.getKakaoAccount();
        Profile profile = account.getProfile();
        return new User(null,
            account.getName(),
            profile.getNickname(),
            PhoneNumber.from(account.getPhoneNumber()),
            resource.getId());
    }
}
