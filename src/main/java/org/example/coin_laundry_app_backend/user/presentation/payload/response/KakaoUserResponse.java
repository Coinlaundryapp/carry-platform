package org.example.coin_laundry_app_backend.user.presentation.payload.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoUserResponse {

    private Long id;
    @JsonProperty("connected_at")
    private LocalDateTime connectedAt;
    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KakaoAccount {

        private Profile profile;
        private String name;
        @JsonProperty("has_email")
        private Boolean hasEmail;
        @JsonProperty("is_email_valid")
        private Boolean isEmailValid;
        @JsonProperty("is_email_verified")
        private Boolean isEmailVerified;
        private String email;
        @JsonProperty("age_range")
        private String ageRange;
        @JsonProperty("has_birthday")
        private Boolean hasBirthday;
        @JsonProperty("birthday")
        private String birthday;
        @JsonProperty("birthday_type")
        private String birthdayType;
        private String gender;
        @JsonProperty("phone_number")
        private String phoneNumber;
        private String ci;
        @JsonProperty("ci_authenticated_at")
        private LocalDateTime ciAuthenticatedAt;

        @Getter
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Profile {

            private String nickname;
            @JsonProperty("thumbnail_image_url")
            private String thumbnailImageUrl;
            @JsonProperty("profile_image_url")
            private String profileImageUrl;
            @JsonProperty("is_default_image")
            private Boolean isDefaultImage;
            @JsonProperty("is_default_nickname")
            private Boolean isDefaultNickname;
        }
    }
}
