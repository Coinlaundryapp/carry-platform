package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import java.time.LocalDateTime;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.data.VerificationCodeData;

@Getter
public class VerificationCode {

    private static final long EXPIRED_MINUTES = 3;
    private final String phoneNumber;
    private final String code;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiredAt;
    private Long id;

    public VerificationCode(String phoneNumber, String code) {
        var current = LocalDateTime.now();
        this.phoneNumber = phoneNumber;
        this.code = code;
        this.createdAt = current;
        this.expiredAt = current.plusMinutes(EXPIRED_MINUTES);
    }

    public VerificationCode(VerificationCodeData data) {
        this.id = data.getId();
        this.phoneNumber = data.getPhoneNumber();
        this.code = data.getCode();
        this.createdAt = data.getCreatedAt();
        this.expiredAt = data.getExpiredAt();
    }

    public boolean isExpired(LocalDateTime current) {
        return expiredAt.isBefore(current);
    }

    public VerificationCodeData toDataEntity() {
        return new VerificationCodeData(id, phoneNumber, code, createdAt, expiredAt);
    }
}
