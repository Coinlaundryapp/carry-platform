package com.carry_laundry.carry_backend.user.repository.converter;

import com.carry_laundry.carry_backend.user.domain.value.PhoneNumber;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.lang.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PhoneNumberConverter {

    @ReadingConverter
    public static class PhoneNumberReadConverter implements Converter<String, PhoneNumber> {

        @Override
        public PhoneNumber convert(@NonNull String source) {
            return PhoneNumber.from(source);
        }
    }

    @WritingConverter
    public static class PhoneNumberWriteConverter implements Converter<PhoneNumber, String> {

        @Override
        public String convert(@NonNull PhoneNumber source) {
            return source.toString();
        }
    }
}
