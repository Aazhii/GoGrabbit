package com.gograbbit.search;

import java.util.List;

/** Frozen wire contract for {@code GET /search/catalog}. */
public record SearchCatalogResponse(List<SearchTypeSpec> types) {
}
