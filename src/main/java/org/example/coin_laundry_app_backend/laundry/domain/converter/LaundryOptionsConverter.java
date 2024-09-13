package org.example.coin_laundry_app_backend.laundry.domain.converter;

import java.util.Arrays;
import java.util.List;
import org.example.coin_laundry_app_backend.laundry.domain.model.enums.LaundryOption;

public class LaundryOptionsConverter {

    public List<LaundryOption> readCovert(String source) {
        return Arrays.stream(source.replaceAll("[{}]", "").split(","))
            .map(LaundryOption::valueOf)
            .toList();
    }

}
