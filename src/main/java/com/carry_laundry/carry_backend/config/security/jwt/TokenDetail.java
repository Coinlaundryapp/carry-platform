package com.carry_laundry.carry_backend.config.security.jwt;

import java.util.Arrays;

public record TokenDetail(long userId, Long[] acceptedTerms) {

    public TokenDetail {
        if (acceptedTerms == null) {
            throw new IllegalArgumentException("acceptedTerms must not be null");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TokenDetail that)) {
            return false;
        }

        return userId() == that.userId() && Arrays.equals(acceptedTerms(),
            that.acceptedTerms());
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(userId());
        result = 31 * result + Arrays.hashCode(acceptedTerms());
        return result;
    }

    @Override
    public String toString() {
        return "TokenDetail{" +
            "userId=" + userId +
            ", acceptedTerms=" + Arrays.toString(acceptedTerms) +
            '}';
    }
}
