package com.carry_laundry.carry_backend.user.domain.converter;

import com.carry_laundry.carry_backend.user.domain.model.entity.data.UserData;
import com.carry_laundry.carry_backend.user.domain.model.entity.domainmodel.User;
import com.carry_laundry.carry_backend.user.domain.model.value.PhoneNumber;
import com.carry_laundry.carry_backend.user.domain.model.value.UserInfo;

public class UserConverter {

    public static User toDomain(UserData userData) {
        return User.builder()
                .id(userData.getId())
                .name(userData.getName())
                .nickname(userData.getNickname())
                .phoneNumber(PhoneNumber.from(userData.getPhoneNumber()))
                .userInfo(UserInfo.from(userData))
                .build();
    }

    public static UserData toData(User user) {
        UserInfo userInfo = user.getUserInfo();
        return UserData.builder()
                .id(user.getId())
                .name(user.getName())
                .nickname(user.getNickname())
                .phoneNumber(user.getPhoneNumber().toString())
                .kakaoId(userInfo.getId())
                .thumbnailImageUrl(userInfo.getThumbnailImageUrl())
                .profileImageUrl(userInfo.getProfileImageUrl())
                .connectedAt(userInfo.getConnectedAt())
                .hasEmail(userInfo.getHasEmail())
                .isEmailValid(userInfo.getIsEmailValid())
                .isEmailVerified(userInfo.getIsEmailVerified())
                .email(userInfo.getEmail())
                .ageRange(userInfo.getAgeRange())
                .hasBirthday(userInfo.getHasBirthday())
                .birthday(userInfo.getBirthday())
                .birthdayType(userInfo.getBirthdayType())
                .gender(userInfo.getGender())
                .ci(userInfo.getCi())
                .ciAuthenticatedAt(userInfo.getCiAuthenticatedAt())
                .build();
    }
}
