package com.gograbbit.search;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the backend and the frontend need to know about one GitHub search
 * endpoint. Built once in {@link SearchCatalog} and served verbatim by
 * {@code GET /search/catalog}.
 *
 * @param sorts                    allowed {@code sort} values; always begins with
 *                                 {@code best-match} except for topics, which supports
 *                                 no sorting at all and gets an empty list
 * @param supportsOrder            whether {@code order=asc|desc} is meaningful (false for topics).
 *                                 GitHub ignores {@code order} unless {@code sort} is sent.
 * @param requiresAuth             true only for code search, which GitHub rejects without a token
 * @param rateLimitBucket          {@code search} (30/min authenticated) or {@code code_search} (10/min)
 * @param requiresRepositoryId     true only for labels, which needs a numeric {@code repository_id}
 * @param requiresSearchTerm       true only for code, which needs at least one search term
 * @param supportsBooleanOperators true only for issues; every other type is implicit-AND with
 *                                 comma-OR inside a single qualifier
 */
public record SearchTypeSpec(
        SearchType type,
        String slug,
        String path,
        String label,
        List<SortSpec> sorts,
        boolean supportsOrder,
        boolean requiresAuth,
        String rateLimitBucket,
        boolean requiresRepositoryId,
        boolean requiresSearchTerm,
        boolean supportsBooleanOperators,
        List<GroupSpec> groups,
        List<QualifierSpec> qualifiers
) {

    public SearchTypeSpec {
        sorts = List.copyOf(sorts);
        groups = List.copyOf(groups);
        qualifiers = List.copyOf(qualifiers);
    }

    /** Lookup by request-parameter name; {@code null} when the key is not part of this type. */
    @JsonIgnore
    public QualifierSpec qualifier(String key) {
        return qualifiersByKey().get(key);
    }

    @JsonIgnore
    public Map<String, QualifierSpec> qualifiersByKey() {
        Map<String, QualifierSpec> map = new LinkedHashMap<>();
        for (QualifierSpec q : qualifiers) {
            map.put(q.key(), q);
        }
        return map;
    }

    @JsonIgnore
    public boolean hasSort(String value) {
        return sorts.stream().anyMatch(s -> s.value().equals(value));
    }
}
