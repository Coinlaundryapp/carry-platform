package com.carry_laundry.carry_backend.user.application.record.address.transform;

public record Pagination(int pageNumber, int pageSize, int totalPages, int totalElements, boolean hasNext) {}
