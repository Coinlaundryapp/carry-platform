package com.carry_laundry.carry_backend.common.presentation.payload;

import java.util.List;
import lombok.Getter;

@Getter
public class CursorPaginationResponse<T> {

    private final List<T> data;
    private final boolean hasNext;
    private final Integer numberOfElements;
    private final Integer size;

    private CursorPaginationResponse(List<T> data, Integer size) {
        this.data = data.subList(0, Math.min(data.size(), size));
        this.numberOfElements = this.data.size();
        this.hasNext = data.size() > size;
        this.size = size;
    }

    public static <T> CursorPaginationResponse<T> fromData(List<T> data, int size) {
        return new CursorPaginationResponse<>(data, size);
    }
}
