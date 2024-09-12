package com.carry_laundry.carry_backend.user.domain.model.entity.data;

import com.carry_laundry.carry_backend.user.domain.model.enums.TermType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@AllArgsConstructor
@Table("terms")
public class TermData {

    @Id
    private Long id;
    private TermType termType;
    private String termInfoTitle;
    private Integer termInfoVersion;
    private String context;
    @CreatedDate
    private LocalDateTime createdAt;

}
