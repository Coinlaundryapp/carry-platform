package org.example.coin_laundry_app_backend.user.domain.model.entity.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@AllArgsConstructor
@Table("users")
public class UserData {
    @Id
    private Long id;
    private String name;
}