package org.example.coin_laundry_app_backend.laundry.domain.model.entity;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.coin_laundry_app_backend.laundry.domain.model.enums.LaundryOption;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("laundry_option_mappings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaundryOptionMapping {

    private Long laundryId;
    private LaundryOption laundryOption;

    public LaundryOptionMapping(Long laundryId, LaundryOption laundryOption) {
        this.laundryId = Objects.requireNonNull(laundryId, "laundryId는 필수 값입니다.");
        this.laundryOption = Objects.requireNonNull(laundryOption, "laundryOption은 필수 값입니다.");
    }
}
