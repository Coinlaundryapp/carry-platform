package org.example.coin_laundry_app_backend.user.domain.model.entity.data;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@AllArgsConstructor
@Table("verification_codes")
@Deprecated(forRemoval = true, since = "2024-08-10")
public class VerificationCodeData {

    @Id
    private Long id;
    private String phoneNumber;
    private String code;
    private LocalDateTime createdAt;
    private LocalDateTime expiredAt;
}
