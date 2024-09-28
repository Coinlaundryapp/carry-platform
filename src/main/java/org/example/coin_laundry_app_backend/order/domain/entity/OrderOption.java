package org.example.coin_laundry_app_backend.order.domain.entity;

import lombok.*;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundryOptionType;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundrySubOptionType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Setter
@Getter
@Table("order_options")
@AllArgsConstructor
@NoArgsConstructor
public class OrderOption {

    @Id
    private Long id;
    private Long orderId;
    private String optionType;
    private String subOptionType;
    private Integer price;
    @CreatedDate
    private String createdAt;
    @LastModifiedDate
    private String updatedAt;

    public static OrderOption create(Long orderId, LaundryOptionType optionType, LaundrySubOptionType subOptionType, Integer price) {
        return OrderOption.builder()
                .orderId(orderId)
                .optionType(optionType.name())
                .subOptionType(subOptionType.name())
                .price(price)
                .build();
    }
}
