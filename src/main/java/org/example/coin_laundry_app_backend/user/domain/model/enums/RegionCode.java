package org.example.coin_laundry_app_backend.user.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RegionCode {

    SOUTH_KOREA("+82");

    private final String value;

    public static RegionCode from(String value) {
        for (RegionCode regionCode : RegionCode.values()) {
            if (regionCode.getValue().equals(value)) {
                return regionCode;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 국가 코드입니다.");
    }
}
