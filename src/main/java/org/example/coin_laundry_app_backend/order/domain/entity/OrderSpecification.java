package org.example.coin_laundry_app_backend.order.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.coin_laundry_app_backend.common.entity.AbstractBaseEntity;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundrySpecType;
import org.springdoc.core.configuration.SpringDocUIConfiguration;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("order_specs")
@AllArgsConstructor
public class OrderSpecification extends AbstractBaseEntity {

    @Id
    private Long id;
    private Long orderId;
    private String specType;
    // TODO Value type is not limited to Long!
    private Long value;
}
