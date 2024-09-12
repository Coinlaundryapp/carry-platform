package com.carry_laundry.carry_backend.user.domain.model.enums;

import lombok.Getter;

@Getter
public enum District {
    EUNPYEONG_GU("은평구"),
    GYEYANG_GU("계양구");

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