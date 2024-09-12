package org.example.coin_laundry_app_backend.laundry.domain.converter;

import java.util.Arrays;
import java.util.List;
import org.example.coin_laundry_app_backend.laundry.domain.model.enums.LaundryOption;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class LaundryOptionReadConverter implements Converter<String, List<LaundryOption>> {

    @Override
    public List<LaundryOption> convert(String source) {
        if (source.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(source.split(","))
            .map(option -> option.replaceAll("[{}]", "").trim())
            .map(LaundryOption::valueOf)
            .toList();
    }
}
