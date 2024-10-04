package com.carry_laundry.carry_backend.laundromat.domain.converter;

import com.carry_laundry.carry_backend.laundromat.domain.enums.LaundromatOption;
import java.util.Arrays;
import java.util.List;

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
