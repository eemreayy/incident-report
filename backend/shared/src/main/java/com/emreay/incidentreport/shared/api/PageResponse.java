package com.emreay.incidentreport.shared.api;

import java.util.List;

/**
 * A page of results, as the API returns it.
 *
 * <p>Deliberately our own shape rather than Spring Data's {@code Page}. Serialising {@code PageImpl}
 * directly ties the public contract to an internal class whose JSON structure is explicitly not
 * guaranteed to stay stable — the frontend would be depending on a shape nobody promised to keep.
 *
 * <p>Lives in the shared kernel because every module that lists something answers with it, and it
 * carries no framework types of its own, so shared keeps depending on nothing but the Spring
 * context.
 *
 * @param content       the items on this page
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalElements total number of items across all pages
 * @param totalPages    total number of pages
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        // Defensive copy: a response object handed to the serialiser must not change underneath it.
        content = content == null ? List.of() : List.copyOf(content);
    }
}
