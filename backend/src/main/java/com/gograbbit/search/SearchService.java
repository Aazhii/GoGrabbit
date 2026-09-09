package com.gograbbit.search;

import tools.jackson.databind.JsonNode;
import com.gograbbit.service.GitHubService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Drives one multi-type search: validate against the catalog, build {@code q},
 * call GitHub, normalize the items, and report paging plus the rate-limit budget.
 *
 * <p>Every rule GitHub would answer with an opaque 403/422 is checked here first so
 * the caller gets a 400 that names what is wrong: unknown qualifier keys, sorts
 * that are not in this endpoint's enum, sorting a type that has no sorts (topics),
 * code search with no token or no search term, labels with no repository, and
 * paging past the universal 1000-result window.
 */
@Service
public class SearchService {

    /** Handled by this service, never passed to {@link SearchQueryBuilder} as qualifiers. */
    private static final Set<String> RESERVED_PARAMS = Set.of("sort", "order", "page", "perPage");

    /** Additionally reserved for labels, which is scoped by repository id rather than a qualifier. */
    private static final Set<String> LABEL_SCOPE_PARAMS = Set.of("repositoryId", "owner", "repo");

    private final SearchCatalog catalog;
    private final SearchQueryBuilder queryBuilder;
    private final SearchResultMapper mapper;
    private final GitHubService gitHubService;
    private final StarFilteredIssueSearch starFilteredIssueSearch;
    private final boolean githubAuthenticated;

    public SearchService(SearchCatalog catalog,
                         SearchQueryBuilder queryBuilder,
                         SearchResultMapper mapper,
                         GitHubService gitHubService,
                         StarFilteredIssueSearch starFilteredIssueSearch,
                         @Value("${github.token:}") String githubToken) {
        this.catalog = catalog;
        this.queryBuilder = queryBuilder;
        this.mapper = mapper;
        this.gitHubService = gitHubService;
        this.starFilteredIssueSearch = starFilteredIssueSearch;
        this.githubAuthenticated = githubToken != null && !githubToken.isBlank();
    }

    public SearchCatalogResponse catalog() {
        return new SearchCatalogResponse(catalog.all());
    }

    public SearchResponse search(SearchType type, Map<String, List<String>> allParams) {
        SearchTypeSpec spec = catalog.spec(type);

        int page = parseInt(allParams, "page", 1);
        int perPage = parseInt(allParams, "perPage", SearchQueryBuilder.DEFAULT_PER_PAGE);
        SearchQueryBuilder.validatePaging(page, perPage);

        String sort = resolveSort(spec, first(allParams, "sort"));
        String order = resolveOrder(spec, first(allParams, "order"), sort);

        Map<String, List<String>> qualifierParams = new LinkedHashMap<>();
        allParams.forEach((key, values) -> {
            if (RESERVED_PARAMS.contains(key)) {
                return;
            }
            if (spec.requiresRepositoryId() && LABEL_SCOPE_PARAMS.contains(key)) {
                return;
            }
            qualifierParams.put(key, values);
        });

        String query = queryBuilder.build(spec, qualifierParams);

        if (spec.requiresAuth() && !githubAuthenticated) {
            throw new IllegalArgumentException("GitHub's " + spec.slug() + " search requires authentication. "
                    + "This server has no GITHUB_TOKEN configured, so the request would be rejected by GitHub "
                    + "with a 403. Set GITHUB_TOKEN and restart.");
        }
        if (spec.requiresSearchTerm() && query.isBlank()) {
            throw new IllegalArgumentException("GitHub's " + spec.slug()
                    + " search needs at least one search term — pass 'q'.");
        }

        // `repoStars` is in the catalog but has no GitHub qualifier, so the query
        // builder deliberately left it out of `q`. It is applied by scanning results
        // instead, which needs a completely different call path.
        String repoStars = first(qualifierParams, "repoStars");
        if (repoStars != null && !repoStars.isBlank()) {
            NumericRange stars = NumericRange.parse("repoStars", repoStars);
            StarFilteredIssueSearch.Result filtered =
                    starFilteredIssueSearch.search(query, sort, order, stars, page, perPage);
            return new SearchResponse(spec.type(), filtered.items(), filtered.totalCount(),
                    page, perPage, filtered.hasNextPage(),
                    // The 1000-result note would be a second, competing explanation of a
                    // short result set; the scan report already says what was covered.
                    false, false,
                    StarFilteredIssueSearch.withSort(query, sort, order),
                    filtered.rateLimit(), filtered.scan());
        }

        Map<String, String> extraParams = new LinkedHashMap<>();
        if (spec.requiresRepositoryId()) {
            extraParams.put("repository_id", String.valueOf(resolveRepositoryId(allParams)));
        }

        RawSearchResult raw = gitHubService.search(spec.path(), query, sort, order, page, perPage, extraParams);
        return toResponse(spec, query, page, perPage, raw, qualifierParams);
    }

    /** Package-private so response shaping can be unit-tested without any HTTP. */
    SearchResponse toResponse(SearchTypeSpec spec, String query, int page, int perPage,
                              RawSearchResult raw, Map<String, List<String>> qualifierParams) {
        List<Map<String, Object>> items = mapper.map(spec.type(), raw.items());

        if (spec.type() == SearchType.ISSUES && wantsIssuesOnly(qualifierParams)) {
            // Unlike /issues/search, PRs are NOT dropped by default here: this endpoint
            // deliberately searches pull requests too. They are only filtered when the
            // caller explicitly asked for type=issue, because GitHub's index still
            // returns PRs for some issue-shaped queries.
            items = items.stream().filter(item -> !Boolean.TRUE.equals(item.get("isPullRequest"))).toList();
        }

        int totalCount = raw.totalCount();
        boolean resultsCapped = totalCount > SearchQueryBuilder.MAX_REACHABLE_RESULTS;
        int reachable = Math.min(totalCount, SearchQueryBuilder.MAX_REACHABLE_RESULTS);

        long consumed = (long) page * perPage;
        boolean hasNextPage = consumed < reachable
                && consumed + perPage <= SearchQueryBuilder.MAX_REACHABLE_RESULTS;

        return new SearchResponse(spec.type(), items, totalCount, page, perPage,
                hasNextPage, resultsCapped, raw.incompleteResults(), query, raw.rateLimit(), null);
    }

    private static boolean wantsIssuesOnly(Map<String, List<String>> qualifierParams) {
        List<String> type = qualifierParams.get("type");
        return type != null && !type.isEmpty() && "issue".equalsIgnoreCase(type.getFirst().trim());
    }

    /**
     * @return the GitHub {@code sort} value to send, or null for best match (which
     * means sending neither {@code sort} nor {@code order})
     */
    private static String resolveSort(SearchTypeSpec spec, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (spec.sorts().isEmpty()) {
            throw new IllegalArgumentException("GitHub's " + spec.slug()
                    + " search does not support sorting, so 'sort' cannot be used here.");
        }
        if (!spec.hasSort(value)) {
            throw new IllegalArgumentException("Invalid 'sort': '" + raw + "' is not valid for type '"
                    + spec.slug() + "'. Expected one of: "
                    + String.join(", ", spec.sorts().stream().map(SortSpec::value).toList()) + ".");
        }
        return SortSpec.BEST_MATCH.equals(value) ? null : value;
    }

    private static String resolveOrder(SearchTypeSpec spec, String raw, String sort) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!spec.supportsOrder()) {
            throw new IllegalArgumentException("GitHub's " + spec.slug()
                    + " search does not support ordering, so 'order' cannot be used here.");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(value) && !"desc".equals(value)) {
            throw new IllegalArgumentException("Invalid 'order': '" + raw + "'. Expected asc or desc.");
        }
        // GitHub ignores `order` without a `sort`, so don't bother sending it.
        return sort == null ? null : value;
    }

    /**
     * Labels search is scoped by a numeric repository id. Callers may pass it
     * directly, or pass {@code owner} + {@code repo} and have it resolved through
     * {@code GET /repos/{owner}/{repo}} — deliberately uncached, since a rename or
     * transfer would leave a stale id pointing somewhere surprising.
     */
    private int resolveRepositoryId(Map<String, List<String>> allParams) {
        String repositoryId = first(allParams, "repositoryId");
        if (repositoryId != null && !repositoryId.isBlank()) {
            try {
                return Integer.parseInt(repositoryId.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid 'repositoryId': '" + repositoryId + "'. Expected GitHub's numeric repository id.");
            }
        }

        String owner = first(allParams, "owner");
        String repo = first(allParams, "repo");
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("Label search must be scoped to one repository: pass "
                    + "'repositoryId', or pass 'owner' and 'repo' and this API will resolve the id for you.");
        }

        JsonNode repository;
        try {
            repository = gitHubService.getRepository(owner.trim(), repo.trim());
        } catch (HttpClientErrorException.NotFound ex) {
            throw new IllegalArgumentException("No such repository: '" + owner.trim() + "/" + repo.trim()
                    + "'. GitHub does not resolve renamed repositories, so check the current owner/repo name.");
        }
        if (repository == null || repository.get("id") == null || !repository.get("id").isNumber()) {
            throw new IllegalArgumentException("GitHub returned no numeric id for repository '"
                    + owner.trim() + "/" + repo.trim() + "'.");
        }
        return repository.get("id").asInt();
    }

    private static String first(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static int parseInt(Map<String, List<String>> params, String key, int fallback) {
        String raw = first(params, key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid '" + key + "': '" + raw + "'. Expected a whole number.");
        }
    }
}
