package org.example.coin_laundry_app_backend.laundry.domain.converter;

import org.example.coin_laundry_app_backend.laundry.domain.model.enums.LaundryOption;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class LaundryOptionReadConverter implements Converter<String, LaundryOption> {

    @Override
    public LaundryOption convert(String source) {
        return LaundryOption.valueOf(source);
    }
}
