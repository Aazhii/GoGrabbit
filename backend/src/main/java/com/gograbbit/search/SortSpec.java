package com.gograbbit.search;

/**
 * One allowed {@code sort} value for a search type.
 *
 * <p>GitHub's sort enums differ per endpoint and a wrong value is a 422, so the
 * catalog is the single source of truth and {@code SearchService} rejects
 * anything not listed here with a 400 before the call goes out.
 */
public record SortSpec(String value, String label) {

    /**
     * The synthetic first entry present on every type except topics. Selecting it
     * means: send neither {@code sort} nor {@code order} to GitHub (relevance order).
     */
    public static final String BEST_MATCH = "best-match";

    public static SortSpec bestMatch() {
        return new SortSpec(BEST_MATCH, "Best match");
    }
}
