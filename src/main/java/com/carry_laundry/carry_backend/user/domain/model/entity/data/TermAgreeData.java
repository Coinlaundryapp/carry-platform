package com.carry_laundry.carry_backend.user.domain.model.entity.data;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("term_agrees")
@AllArgsConstructor
public class TermAgreeData {

    @Id
    private Long id;
    private Long userId;
    private Long termId;
    private Boolean agreeYn;
    private LocalDateTime updatedAt;
}
