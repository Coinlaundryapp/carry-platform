package org.example.coin_laundry_app_backend.user.presentation.payload.response.address;

import org.example.coin_laundry_app_backend.user.application.record.address.transform.Content;
import org.example.coin_laundry_app_backend.user.application.record.address.transform.Pagination;

import java.util.List;

public record SearchAddressResponse(List<Content> content, Pagination pagination) {}
