package com.gograbbit.dto;

import java.time.Instant;
import java.util.List;

/**
 * Frozen wire contract for {@code GET /issues/search} — the frontend is built
 * against these exact field names.
 *
 * @param totalCount        GitHub's {@code total_count}. This is the size of the
 *                          match set, NOT the number of results you can page to:
 *                          only the first 1000 are reachable (see {@link #resultsCapped}).
 * @param hasNextPage       true when more results exist AND the next page is still
 *                          inside the 1000-result reachable window.
 * @param resultsCapped     true when {@code totalCount} exceeds the 1000 reachable results.
 * @param incompleteResults GitHub's {@code incomplete_results} — the search timed
 *                          out upstream and this page is partial.
 * @param query             the exact {@code q} string sent to GitHub, for debuggability.
 */
public record IssueSearchResponse(
        List<Item> items,
        int totalCount,
        int page,
        int perPage,
        boolean hasNextPage,
        boolean resultsCapped,
        boolean incompleteResults,
        String query
) {

    /** @param author null for deleted ("ghost") users. */
    public record Item(
            long id,
            int number,
            String title,
            String url,
            String state,
            String owner,
            String repo,
            String repositoryFullName,
            List<Label> labels,
            Instant createdAt,
            Instant updatedAt,
            int comments,
            User author,
            List<User> assignees
    ) {
    }

    public record Label(String name, String color) {
    }

    public record User(String login, String avatarUrl, String url) {
    }
}
