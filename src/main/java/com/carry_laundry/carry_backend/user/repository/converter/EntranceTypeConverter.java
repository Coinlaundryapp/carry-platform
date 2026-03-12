package com.carry_laundry.carry_backend.user.repository.converter;

import com.carry_laundry.carry_backend.user.domain.enums.EntranceType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EntranceTypeConverter {

    @ReadingConverter
    public static class EntranceTypeReadConverter implements Converter<String, EntranceType> {

        @Override
        public EntranceType convert(@NonNull String source) {
            return EntranceType.valueOf(source);
        }
    }

}
