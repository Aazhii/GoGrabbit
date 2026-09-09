package com.gograbbit.search;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;

/**
 * The seven GitHub search endpoints this backend exposes.
 *
 * <p>{@link #slug()} is both the URL segment of {@code GET /search/{slug}} and the
 * value serialised into the JSON contract (see {@link #jsonValue()}), so
 * {@code SearchTypeSpec.type} and {@code SearchTypeSpec.slug} always agree.
 */
public enum SearchType {

    // Declaration order IS the order the catalog and the UI's tab strip use, and
    // the first entry is the tab the app opens on. Issues leads deliberately: the
    // product exists so you can search for the right issue directly instead of
    // browsing repositories hoping to find one.
    ISSUES("issues", "/search/issues", "Issues & pull requests"),
    REPOSITORIES("repositories", "/search/repositories", "Repositories"),
    USERS("users", "/search/users", "Users & organizations"),
    CODE("code", "/search/code", "Code"),
    COMMITS("commits", "/search/commits", "Commits"),
    TOPICS("topics", "/search/topics", "Topics"),
    LABELS("labels", "/search/labels", "Labels");

    private final String slug;
    private final String path;
    private final String label;

    SearchType(String slug, String path, String label) {
        this.slug = slug;
        this.path = path;
        this.label = label;
    }

    public String slug() {
        return slug;
    }

    /** GitHub REST path, e.g. {@code /search/repositories}. */
    public String path() {
        return path;
    }

    public String label() {
        return label;
    }

    @JsonValue
    String jsonValue() {
        return slug;
    }

    /**
     * @throws IllegalArgumentException naming the valid slugs, so an unknown
     *                                  {@code {type}} path segment becomes a useful 400.
     */
    public static SearchType fromSlug(String raw) {
        if (raw != null) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (SearchType type : values()) {
                if (type.slug.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("Unknown search type '" + raw + "'. Valid types are: "
                + String.join(", ", Arrays.stream(values()).map(SearchType::slug).toList()) + ".");
    }
}
