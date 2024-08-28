package org.example.coin_laundry_app_backend.user.application.record.address.fetch;

import java.util.List;

public record FetchApiResponse(
        List<Document> documents,
        Meta meta
) {}