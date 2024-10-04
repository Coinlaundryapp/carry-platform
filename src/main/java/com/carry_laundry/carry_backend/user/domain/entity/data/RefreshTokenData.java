package com.carry_laundry.carry_backend.user.domain.entity.data;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@AllArgsConstructor
@Table("refresh_tokens")
public class RefreshTokenData {

    @Id
    private Long id;
    private Long userId;
    private String value;
    private LocalDateTime expiryAt;
}
