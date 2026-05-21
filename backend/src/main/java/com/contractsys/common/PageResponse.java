package com.contractsys.common;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> records, int page, int size, long total) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }
}

