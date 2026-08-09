package com.emreay.incidentreport.shared.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResponseTest {

    @Test
    void carriesTheItemsAndThePagingNumbers() {
        PageResponse<String> page = new PageResponse<>(List.of("a", "b"), 2, 5, 42L, 9);

        assertThat(page.content()).containsExactly("a", "b");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(42L);
        assertThat(page.totalPages()).isEqualTo(9);
    }

    /**
     * The response is built from whatever the repository handed back, and serialisation happens
     * later, on another thread's schedule. If the source list were kept by reference, a page could
     * change between being built and being written.
     */
    @Test
    void doesNotKeepTheCallersList() {
        List<String> source = new ArrayList<>(List.of("a"));

        PageResponse<String> page = new PageResponse<>(source, 0, 20, 1L, 1);
        source.add("b");

        assertThat(page.content()).containsExactly("a");
        assertThatThrownBy(() -> page.content().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** An empty page is an empty list, never null — callers should not have to check. */
    @Test
    void treatsMissingContentAsAnEmptyPage() {
        assertThat(new PageResponse<String>(null, 0, 20, 0L, 0).content()).isEmpty();
    }
}
