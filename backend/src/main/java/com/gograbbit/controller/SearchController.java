package com.gograbbit.controller;

import com.gograbbit.search.SearchCatalogResponse;
import com.gograbbit.search.SearchResponse;
import com.gograbbit.search.SearchService;
import com.gograbbit.search.SearchType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Multi-type GitHub search across all seven search endpoints.
 *
 * <p>Deliberately spec-driven: there is no {@code @RequestParam} per qualifier
 * (there are ~116 of them). The whole query string arrives as a map and is
 * validated against {@code SearchCatalog}, so adding a qualifier is a catalog
 * entry and nothing else. {@code GET /search/catalog} publishes that same catalog
 * so the frontend can build its form from it.
 *
 * <p>This lives alongside the older single-type {@code GET /issues/search}
 * ({@code IssueSearchController}), which is unchanged and still in use.
 *
 * <p>All validation throws {@link IllegalArgumentException}; {@code GlobalExceptionHandler}
 * turns that into a 400 carrying the message.
 */
@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * The static description of every search type: sorts, rate-limit bucket, groups
     * and qualifiers. Mapped before {@code /search/{type}} because Spring prefers the
     * literal path over the template, so {@code catalog} is never read as a type slug.
     */
    @GetMapping("/search/catalog")
    public SearchCatalogResponse catalog() {
        return searchService.catalog();
    }

    /**
     * @param type      one of the catalog slugs; an unknown slug is a 400 listing the valid ones
     * @param allParams the raw query string. {@code sort}, {@code order}, {@code page} and
     *                  {@code perPage} are reserved; for {@code labels}, so are
     *                  {@code repositoryId}, {@code owner} and {@code repo}. Everything else
     *                  must be a qualifier key from that type's catalog entry.
     */
    @GetMapping("/search/{type}")
    public SearchResponse search(@PathVariable String type,
                                 @RequestParam MultiValueMap<String, String> allParams) {
        SearchType searchType = SearchType.fromSlug(type);
        Map<String, List<String>> params = allParams == null ? Map.of() : allParams;
        return searchService.search(searchType, params);
    }
}
