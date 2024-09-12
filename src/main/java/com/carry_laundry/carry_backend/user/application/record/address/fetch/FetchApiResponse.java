package com.carry_laundry.carry_backend.user.application.record.address.fetch;

import java.util.List;

public record FetchApiResponse(
        List<Document> documents,
        Meta meta
) {}