package com.carry_laundry.carry_backend.user.presentation.payload.response.address;

import com.carry_laundry.carry_backend.user.application.record.address.transform.Content;
import com.carry_laundry.carry_backend.user.application.record.address.transform.Pagination;
import java.util.List;

public record SearchAddressResponse(List<Content> content, Pagination pagination) {

}
