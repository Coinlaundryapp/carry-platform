package com.carry_laundry.carry_backend.user.domain.converter;

import com.carry_laundry.carry_backend.user.domain.enums.EntranceType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class EntranceTypeReadConverter implements Converter<String, EntranceType> {

    @Override
    public EntranceType convert(@NonNull String source) {
        return EntranceType.valueOf(source);
    }
}
