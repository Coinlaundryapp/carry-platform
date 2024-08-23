package org.example.coin_laundry_app_backend.user.application.record.address.transform;

public record Pagination(int pageNumber, int pageSize, int totalPages, int totalElements, boolean hasNext) {}
