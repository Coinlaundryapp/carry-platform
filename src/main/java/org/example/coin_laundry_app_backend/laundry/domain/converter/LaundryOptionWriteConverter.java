package org.example.coin_laundry_app_backend.laundry.domain.converter;

import java.util.List;
import org.example.coin_laundry_app_backend.laundry.domain.model.enums.LaundryOption;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public class LaundryOptionWriteConverter implements Converter<List<LaundryOption>, String[]> {

    @Override
    public String[] convert(List<LaundryOption> source) {
        return source.stream()
            .map(Enum::name)
            .toArray(String[]::new);
    }
}
