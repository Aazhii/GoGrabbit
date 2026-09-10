package com.gograbbit.search;

import tools.jackson.databind.JsonNode;

/**
 * One page of GitHub's GraphQL issue search, kept as an untyped tree for the same
 * reason as {@link RawSearchResult}: the nodes are flattened straight into the
 * normalized wire shape by {@link SearchResultMapper}.
 *
 * @param nodes      the {@code search.nodes} array, or null when GitHub sent none
 * @param issueCount total matches upstream — like REST's {@code total_count}, this can
 *                   far exceed the 1000 results that are actually reachable
 * @param hasNextPage whether another cursor page exists
 * @param endCursor  cursor to pass as {@code after} for the next page; null at the end
 * @param rateLimit  parsed from the response headers ({@code graphql} bucket)
 */
public record GraphQlIssuePage(
        JsonNode nodes,
        int issueCount,
        boolean hasNextPage,
        String endCursor,
        RateLimitInfo rateLimit
) {

    public static GraphQlIssuePage empty(RateLimitInfo rateLimit) {
        return new GraphQlIssuePage(null, 0, false, null, rateLimit);
    }

    public int size() {
        return nodes == null || !nodes.isArray() ? 0 : nodes.size();
    }
}
