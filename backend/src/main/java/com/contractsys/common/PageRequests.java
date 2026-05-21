package com.contractsys.common;

import org.springframework.data.domain.PageRequest;

public final class PageRequests {
    private static final int MAX_SIZE = 200;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size) {
        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
