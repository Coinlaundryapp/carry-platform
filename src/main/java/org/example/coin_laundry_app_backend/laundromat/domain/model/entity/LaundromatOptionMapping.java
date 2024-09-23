package org.example.coin_laundry_app_backend.laundromat.domain.model.entity;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.coin_laundry_app_backend.laundromat.domain.model.enums.LaundromatOption;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("laundromat_option_mappings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaundromatOptionMapping {

    private Long laundromatId;
    private LaundromatOption laundromatOption;

    public LaundromatOptionMapping(Long laundromatId, LaundromatOption laundromatOption) {
        this.laundromatId = Objects.requireNonNull(laundromatId, "laundromatId는 필수 값입니다.");
        this.laundromatOption = Objects.requireNonNull(laundromatOption,
            "laundromatOption은 필수 값입니다.");
    }
}
