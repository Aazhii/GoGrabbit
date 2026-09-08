package com.gograbbit.search;

import tools.jackson.databind.JsonNode;

/**
 * One GitHub search response, kept as an untyped tree.
 *
 * <p>Seven endpoints return seven different item shapes; binding each to its own
 * record would be ~7 more DTO trees for fields that are then immediately
 * flattened into the normalized wire shapes in {@link SearchResultMapper}. The
 * envelope itself ({@code total_count}, {@code incomplete_results}, {@code items})
 * is identical everywhere, so only the items need per-type handling.
 *
 * @param body      the parsed JSON envelope, or null if GitHub returned no body
 * @param rateLimit parsed from the response headers; null when absent
 */
public record RawSearchResult(JsonNode body, RateLimitInfo rateLimit) {

    public int totalCount() {
        return body == null ? 0 : body.path("total_count").asInt(0);
    }

    public boolean incompleteResults() {
        return body != null && body.path("incomplete_results").asBoolean(false);
    }

    public JsonNode items() {
        return body == null ? null : body.get("items");
    }
}
