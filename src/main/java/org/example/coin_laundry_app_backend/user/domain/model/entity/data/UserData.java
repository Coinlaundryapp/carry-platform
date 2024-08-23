package org.example.coin_laundry_app_backend.user.domain.model.entity.data;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.ShippingAddress;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Builder
@AllArgsConstructor
@Table("users")
public class UserData {

    @Id
    private Long id;
    private String name;
    private String nickname;
    private String phoneNumber;

    private final Long kakaoId;
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
}