package com.carry_laundry.carry_backend.user.presentation.payload.request.address;


import org.springframework.lang.NonNull;

public record AddressSearchRequest (
    @NonNull String query,
    @NonNull int pageNumber,
    @NonNull int pageSize
) {}
