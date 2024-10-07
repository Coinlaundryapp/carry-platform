package com.carry_laundry.carry_backend.review.domain.enums;

import lombok.Getter;

@Getter
public enum ReviewRating {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5);

    private final int value;

    ReviewRating(int value) {
        this.value = value;
    }

    public static ReviewRating from(int value) {
        return switch (value) {
            case 1 -> ONE;
            case 2 -> TWO;
            case 3 -> THREE;
            case 4 -> FOUR;
            case 5 -> FIVE;
            default -> throw new IllegalArgumentException("Unexpected value: " + value);
        };
    }

}
