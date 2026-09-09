package com.gograbbit.service;

import com.gograbbit.search.GraphQlIssuePage;
import com.gograbbit.search.RateLimitInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GitHub's GraphQL API, used for exactly one thing REST cannot do: return an issue
 * together with its repository's star count.
 *
 * <p>This is a deliberate exception to the REST-everywhere decision in
 * {@code docs/DECISIONS.md}, for two measured reasons:
 * <ul>
 *   <li><b>REST cannot answer the question at all.</b> {@code /search/issues} has no
 *       {@code stars:} qualifier and its items carry only a {@code repository_url},
 *       not the repository. Worse, putting {@code stars:>1000} in an issue query is
 *       not rejected — GitHub parses it as free text and returns issues from
 *       repositories with single-digit star counts. Verified against the live API.</li>
 *   <li><b>Budget.</b> Post-filtering means scanning several pages. On REST that is
 *       one request per page out of the search bucket's 30/minute. In GraphQL a
 *       100-item page costs 1 point out of 5000/hour, so an exhaustive scan of the
 *       whole reachable window costs about 10 points.</li>
 * </ul>
 *
 * <p>GraphQL is authenticated-only — there is no anonymous access — so callers must
 * check for a configured token before relying on this.
 */
@Service
public class GitHubGraphQLService {

    private static final Logger log = LoggerFactory.getLogger(GitHubGraphQLService.class);

    /** GraphQL caps connection page size at 100, same as REST. */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Both fragments carry the same field set: GraphQL's {@code ISSUE} search returns
     * {@code PullRequest} nodes as well as {@code Issue} nodes, and dropping the ones
     * we did not spell out would silently lose results for a query like
     * {@code is:pr}. {@code stateReason} exists only on Issue, {@code isDraft} only on
     * PullRequest.
     */
    private static final String ISSUE_SEARCH_QUERY = """
            query($q: String!, $n: Int!, $after: String) {
              search(query: $q, type: ISSUE, first: $n, after: $after) {
                issueCount
                pageInfo { hasNextPage endCursor }
                nodes {
                  __typename
                  ... on Issue {
                    databaseId number title url state stateReason
                    createdAt updatedAt closedAt
                    comments { totalCount }
                    reactions { totalCount }
                    author { login avatarUrl url }
                    labels(first: 20) { nodes { name color } }
                    assignees(first: 10) { nodes { login avatarUrl url } }
                    milestone { title }
                    repository {
                      nameWithOwner url stargazerCount isArchived
                      owner { login }
                      primaryLanguage { name }
                    }
                  }
                  ... on PullRequest {
                    databaseId number title url state isDraft
                    createdAt updatedAt closedAt
                    comments { totalCount }
                    reactions { totalCount }
                    author { login avatarUrl url }
                    labels(first: 20) { nodes { name color } }
                    assignees(first: 10) { nodes { login avatarUrl url } }
                    milestone { title }
                    repository {
                      nameWithOwner url stargazerCount isArchived
                      owner { login }
                      primaryLanguage { name }
                    }
                  }
                }
              }
            }
            """;

    private final RestClient githubRestClient;
    private final GitHubService gitHubService;
    private final boolean authenticated;

    public GitHubGraphQLService(RestClient githubRestClient,
                                GitHubService gitHubService,
                                @Value("${github.token:}") String githubToken) {
        this.githubRestClient = githubRestClient;
        this.gitHubService = gitHubService;
        this.authenticated = githubToken != null && !githubToken.isBlank();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * One page of an issue search.
     *
     * @param q     a GitHub issue search query. GraphQL has no {@code sort} argument,
     *              so any ordering must already be embedded as a {@code sort:} qualifier
     *              in this string (see {@code StarFilteredIssueSearch}).
     * @param first page size, 1..{@value #MAX_PAGE_SIZE}
     * @param after opaque cursor from the previous page, or null to start
     */
    public GraphQlIssuePage searchIssues(String q, int first, String after) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("q", q);
        variables.put("n", Math.clamp(first, 1, MAX_PAGE_SIZE));
        variables.put("after", after);

        Map<String, Object> body = Map.of("query", ISSUE_SEARCH_QUERY, "variables", variables);

        ResponseEntity<JsonNode> response = gitHubService.executeWithRetry(() -> githubRestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class));

        JsonNode payload = response.getBody();
        // GraphQL reports failures in a 200 body, so an HTTP-only check would treat a
        // broken query as an empty result set.
        if (payload != null && payload.has("errors") && !payload.path("errors").isEmpty()) {
            throw new IllegalArgumentException("GitHub rejected the GraphQL issue search: "
                    + firstErrorMessage(payload.path("errors")));
        }
        if (payload == null || !payload.path("data").has("search")) {
            log.warn("GraphQL issue search returned no search payload");
            return GraphQlIssuePage.empty(RateLimitInfo.from(response.getHeaders()));
        }

        JsonNode search = payload.path("data").path("search");
        return new GraphQlIssuePage(
                search.get("nodes"),
                search.path("issueCount").asInt(0),
                search.path("pageInfo").path("hasNextPage").asBoolean(false),
                search.path("pageInfo").path("endCursor").isNull()
                        ? null : search.path("pageInfo").path("endCursor").asString(null),
                RateLimitInfo.from(response.getHeaders()));
    }

    private static String firstErrorMessage(JsonNode errors) {
        JsonNode first = errors.get(0);
        if (first == null) {
            return "unknown error";
        }
        String message = first.path("message").asString("unknown error");
        String type = first.path("type").asString(null);
        return type == null ? message : type + ": " + message;
    }
}
