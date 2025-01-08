package com.carry_laundry.carry_backend.config;

import com.carry_laundry.carry_backend.media_resource.domain.converter.ResourceStatusReadConverter;
import com.carry_laundry.carry_backend.term.repository.converter.TermTypeConverter.TermTypeReadConverter;
import com.carry_laundry.carry_backend.term.repository.converter.TermTypeConverter.TermTypeWriteConverter;
import com.carry_laundry.carry_backend.user.domain.converter.EntranceTypeReadConverter;
import com.carry_laundry.carry_backend.user.repository.converter.PhoneNumberConverter.PhoneNumberReadConverter;
import com.carry_laundry.carry_backend.user.repository.converter.PhoneNumberConverter.PhoneNumberWriteConverter;
import io.r2dbc.spi.ConnectionFactory;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@EnableR2dbcRepositories
@EnableR2dbcAuditing
public class R2dbcConfiguration {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory) {
        R2dbcDialect dialect = DialectResolver.getDialect(connectionFactory);
        return R2dbcCustomConversions.of(dialect, List.of(
                new EntranceTypeReadConverter(),
                new ResourceStatusReadConverter(),
                new TermTypeReadConverter(),
                new TermTypeWriteConverter(),
                new PhoneNumberReadConverter(),
                new PhoneNumberWriteConverter()
            )
        );
    }
}
