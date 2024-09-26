package org.example.coin_laundry_app_backend.order.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.coin_laundry_app_backend.common.entity.AbstractBaseEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("order_options")
@AllArgsConstructor
public class OrderOption extends AbstractBaseEntity {

    @Id
    private Long id;
    private Long orderId;
    private String optionType;
    private String subOptionType;
    private Integer price;
}
