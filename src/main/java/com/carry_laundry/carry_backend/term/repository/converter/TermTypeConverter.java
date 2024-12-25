package com.carry_laundry.carry_backend.term.repository.converter;

import com.carry_laundry.carry_backend.term.domain.enums.TermType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.lang.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TermTypeConverter {

    @ReadingConverter
    public static class TermTypeReadConverter implements Converter<String, TermType> {

        @Override
        public TermType convert(@NonNull String source) {
            return TermType.valueOf(source);
        }
    }

    @WritingConverter
    public static class TermTypeWriteConverter implements Converter<TermType, String> {

        @Override
        public String convert(@NonNull TermType source) {
            return source.name();
        }
    }

}
