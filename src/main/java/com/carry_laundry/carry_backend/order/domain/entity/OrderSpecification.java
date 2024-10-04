package com.carry_laundry.carry_backend.order.domain.entity;

import com.carry_laundry.carry_backend.order.application.record.SpecDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Getter
@Setter
@Table("order_specs")
@AllArgsConstructor
@NoArgsConstructor
public class OrderSpecification {

    @Id
    private Long id;
    private Long orderId;
    private String specType;
    // TODO Value type is not limited to Long!
    private Long value;
    @CreatedDate
    private String createdAt;
    @LastModifiedDate
    private String updatedAt;

    public static OrderSpecification create(Long orderId, SpecDescription specDescription) {
        return OrderSpecification.builder()
            .orderId(orderId)
            .specType(specDescription.laundrySpec().name())
            .value(specDescription.value())
            .build();
    }

}
