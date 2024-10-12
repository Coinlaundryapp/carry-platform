package com.carry_laundry.carry_backend.media_resource.domain.converter;

import com.carry_laundry.carry_backend.media_resource.domain.enums.ResourceStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class ResourceStatusReadConverter implements Converter<String, ResourceStatus> {

    @Override
    public ResourceStatus convert(String source) {
        return ResourceStatus.valueOf(source.toUpperCase());
    }
}
