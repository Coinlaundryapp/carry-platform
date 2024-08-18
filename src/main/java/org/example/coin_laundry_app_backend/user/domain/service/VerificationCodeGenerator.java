package org.example.coin_laundry_app_backend.user.domain.service;

import java.security.SecureRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Deprecated(forRemoval = true, since = "2024-08-10")
public class VerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateVerificationCode() {
        var sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
