package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.data.UserData;
import org.example.coin_laundry_app_backend.user.domain.model.value.PhoneNumber;

@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    Long id;
    String username;
    PhoneNumber phoneNumber;
    Boolean commercialYn;
    Boolean locationYn;

    public static User from(UserData userData) {
        return new User(userData.getId(), userData.getUsername(),
            PhoneNumber.from(userData.getPhoneNumber()), userData.getCommercialYn(),
            userData.getLocationYn());
    }

    public UserData toData() {
        return new UserData(id, username, phoneNumber.getValue(), commercialYn, locationYn);
    }
}
