package com.bloodlink.request;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * A page of results, in a shape this project owns.
 *
 * Spring's Page is not serialised directly: its JSON shape is an implementation
 * detail that has changed between versions, and an API contract should not move
 * because a dependency did.
 *
 * @param content       the items on this page
 * @param page          zero-based page number
 * @param size          page size requested
 * @param totalElements how many items match in total
 * @param totalPages    how many pages that makes
 * @param <T>           the item type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * Wraps a Spring page.
     *
     * @param page the page to wrap
     * @param <T>  the item type
     * @return the same data in this project's shape
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
