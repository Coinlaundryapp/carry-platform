package org.example.coin_laundry_app_backend.user.domain.model.enums;

import lombok.Getter;

@Getter
public enum District {
    EUNPYEONG_GU_SEOUL("은평구"),
    GYEYANG_GU_INCHEON("계양구");

    private final String description;

    District(String description) {
        this.description = description;
    }

    public static District from(String description) {
        for (District district : District.values()) {
            if (district.getDescription().equals(description)) {
                return district;
            }
        }
        throw new IllegalArgumentException("No enum constant with name: " + description);
    }
}