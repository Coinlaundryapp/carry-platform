package org.example.coin_laundry_app_backend.user.domain.converter;

import org.example.coin_laundry_app_backend.user.domain.model.enums.EntranceType;
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
