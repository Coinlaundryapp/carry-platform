package org.example.coin_laundry_app_backend.laundromat.domain.converter;

import java.util.Arrays;
import java.util.List;
import org.example.coin_laundry_app_backend.laundromat.domain.model.enums.LaundromatOption;

public class LaundromatOptionsConverter {

    public List<LaundromatOption> readCovert(String source) {
        if (source == null || source.equals("{}")) {
            return List.of();
        }
        return Arrays.stream(source.replaceAll("[{}]", "").split(","))
            .map(LaundromatOption::valueOf)
            .toList();
    }

}
