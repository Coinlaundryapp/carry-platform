package org.example.coin_laundry_app_backend.laundry.domain.converter;

import org.example.coin_laundry_app_backend.laundry.domain.model.enums.LaundryOption;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public class LaundryOptionWriteConverter implements Converter<LaundryOption, String> {

    @Override
    public String convert(LaundryOption source) {
        return source.name();
    }
}
