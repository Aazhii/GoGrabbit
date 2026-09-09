package com.gograbbit.search;

import com.gograbbit.service.GitHubGraphQLService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Issue search filtered by the star count of the owning repository.
 *
 * <p>GitHub cannot do this. {@code /search/issues} has no {@code stars:} qualifier,
 * and including one is not an error — it is parsed as free text, so the query
 * {@code label:"good first issue" stars:>1000} happily returns issues from
 * repositories with two stars. The only way to get a trustworthy answer is to fetch
 * issues, look up each one's repository, and filter here.
 *
 * <p>That is done over GraphQL because it returns the repository inline with the
 * issue, and because a page costs one point out of 5000/hour rather than one request
 * out of the search bucket's 30/minute.
 *
 * <p><b>Why it scans rather than filtering a single page:</b> new issues are
 * overwhelmingly opened in small repositories. In a sample of the 20 most recently
 * created {@code good first issue}s, not one belonged to a repository with 1000+
 * stars. Filtering a single 30-item page would therefore return nothing almost every
 * time, and look broken. So pages are pulled until enough matches accumulate or the
 * budget runs out, and {@link ScanStats} reports exactly what happened.
 */
@Component
public class StarFilteredIssueSearch {

    /** Always pull full pages: the scan is bounded by points, and fewer round trips is faster. */
    private static final int SCAN_PAGE_SIZE = GitHubGraphQLService.MAX_PAGE_SIZE;

    private final GitHubGraphQLService graphQl;
    private final SearchResultMapper mapper;
    private final int pageBudget;

    public StarFilteredIssueSearch(GitHubGraphQLService graphQl,
                                   SearchResultMapper mapper,
                                   @Value("${github.graphql.issue-scan-page-budget:5}") int pageBudget) {
        this.graphQl = graphQl;
        this.mapper = mapper;
        // 10 pages of 100 is GitHub's entire reachable window, so there is never a
        // reason to allow more; below 1 the feature could not work at all.
        this.pageBudget = Math.clamp(pageBudget, 1, 10);
    }

    /**
     * @param query   the GitHub issue query as built from the caller's filters, WITHOUT
     *                any {@code sort:} qualifier
     * @param sort    endpoint sort value ({@code created}, {@code updated}, ...) or null
     *                for relevance
     * @param order   {@code asc}/{@code desc}; ignored when {@code sort} is null
     * @param stars   the range to require of each issue's repository
     * @param page    1-based page over the FILTERED results
     * @param perPage page size over the FILTERED results
     */
    public Result search(String query, String sort, String order, NumericRange stars,
                         int page, int perPage) {
        if (!graphQl.isAuthenticated()) {
            throw new IllegalArgumentException("Filtering issues by repository stars needs GitHub's "
                    + "GraphQL API, which has no anonymous access. This server has no GITHUB_TOKEN "
                    + "configured — set one and restart, or remove the 'repoStars' filter.");
        }

        String scanQuery = withSort(query, sort, order);
        // One extra match is enough to know whether a further page exists.
        int needed = page * perPage + 1;

        List<Map<String, Object>> matches = new ArrayList<>();
        String cursor = null;
        int scannedIssues = 0;
        int scannedPages = 0;
        boolean exhausted = false;
        RateLimitInfo rateLimit = null;

        while (scannedPages < pageBudget) {
            GraphQlIssuePage pageResult = graphQl.searchIssues(scanQuery, SCAN_PAGE_SIZE, cursor);
            scannedPages++;
            scannedIssues += pageResult.size();
            rateLimit = pageResult.rateLimit();

            for (Map<String, Object> item : mapper.mapGraphQlIssues(pageResult.nodes())) {
                Object starCount = item.get("stars");
                // A repository that has been deleted or hidden since the issue was
                // indexed comes back without one; it cannot be judged, so it is not
                // claimed as a match.
                if (starCount instanceof Number number && stars.matches(number.longValue())) {
                    matches.add(item);
                }
            }

            if (!pageResult.hasNextPage() || pageResult.endCursor() == null) {
                exhausted = true;
                break;
            }
            cursor = pageResult.endCursor();

            if (matches.size() >= needed) {
                break;
            }
        }

        int from = Math.min((page - 1) * perPage, matches.size());
        int to = Math.min(from + perPage, matches.size());
        List<Map<String, Object>> pageItems = List.copyOf(matches.subList(from, to));

        ScanStats scan = new ScanStats(scannedIssues, scannedPages, matches.size(), exhausted, pageBudget);
        return new Result(pageItems, matches.size(), matches.size() > page * perPage, scan, rateLimit);
    }

    /**
     * GraphQL's {@code search} has no {@code sort} argument — ordering is expressed as
     * a {@code sort:field-direction} qualifier inside the query itself.
     */
    static String withSort(String query, String sort, String order) {
        if (sort == null || sort.isBlank()) {
            return query;
        }
        String direction = "asc".equalsIgnoreCase(order) ? "asc" : "desc";
        String qualifier = "sort:" + sort + "-" + direction;
        return query == null || query.isBlank() ? qualifier : query + " " + qualifier;
    }

    /**
     * @param totalCount  the number of matches found within the scanned window — NOT
     *                    GitHub's upstream match count, which is the size of the set
     *                    before the star filter and would overstate the result by
     *                    orders of magnitude
     * @param hasNextPage whether the matches already found extend past this page
     */
    public record Result(
            List<Map<String, Object>> items,
            int totalCount,
            boolean hasNextPage,
            ScanStats scan,
            RateLimitInfo rateLimit
    ) {
    }
}
