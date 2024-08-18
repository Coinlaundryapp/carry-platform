package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class User {
    Long id;
    String username;
}