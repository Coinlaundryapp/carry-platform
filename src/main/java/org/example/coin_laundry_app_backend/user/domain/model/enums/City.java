package org.example.coin_laundry_app_backend.user.domain.model.enums;

import lombok.Getter;

@Getter
public enum City {
    SEOUL_SI("서울특별시"),
    INCHEON_SI("인천광역시"),
    ANYANG_SI("안양시"),
    GIMPO_SI("김포시"),
    BUCHEON_SI("부천시"),
    GWANGMYEONG_SI("광명시"),
    SEONGNAM_SI("성남시"),
    GURI_SI("구리시");

    private final String description;

    City(String description) {
        this.description = description;
    }

    public static City from(String description) {
        for (City city : City.values()) {
            if (city.getDescription().equals(description)) {
                return city;
            }
        }
        throw new IllegalArgumentException("No enum constant with description: " + description);
    }
}
