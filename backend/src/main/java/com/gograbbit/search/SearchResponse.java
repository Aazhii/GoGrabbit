package com.gograbbit.search;

import java.util.List;
import java.util.Map;

/**
 * Frozen wire contract for {@code GET /search/{type}}.
 *
 * @param items             normalized per-type items; see {@link SearchResultMapper}
 * @param totalCount        GitHub's {@code total_count} — the size of the match set, NOT
 *                          how far you can page (see {@link #resultsCapped})
 * @param hasNextPage       true when more results exist AND the next page still starts
 *                          inside GitHub's 1000-result reachable window
 * @param resultsCapped     true when {@code totalCount} exceeds those 1000 reachable results
 * @param incompleteResults GitHub's {@code incomplete_results}: the search timed out
 *                          upstream and this page is partial
 * @param query             the exact {@code q} string sent to GitHub, for debuggability
 * @param rateLimit         parsed from GitHub's response headers; null when it sent none
 */
public record SearchResponse(
        SearchType type,
        List<Map<String, Object>> items,
        int totalCount,
        int page,
        int perPage,
        boolean hasNextPage,
        boolean resultsCapped,
        boolean incompleteResults,
        String query,
        RateLimitInfo rateLimit
) {
}
