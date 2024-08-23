package org.example.coin_laundry_app_backend.user.presentation.payload.request.address;


import lombok.Getter;
import org.springframework.lang.NonNull;

public record AddressSearchRequest (
    @NonNull String query,
    @NonNull int pageNumber,
    @NonNull int pageSize
) {}
