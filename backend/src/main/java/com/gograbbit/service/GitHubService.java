package com.gograbbit.service;

import tools.jackson.databind.JsonNode;
import com.gograbbit.domain.WatchedRepo;
import com.gograbbit.dto.GitHubIssueSearchResponse;
import com.gograbbit.search.RateLimitInfo;
import com.gograbbit.search.RawSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    // GitHub search calls are the only outbound call site today, so a small
    // built-in retry covers transient network blips / 5xx without pulling in
    // Resilience4j for one call — see docs/DECISIONS.md.
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(300);

    private final RestClient githubRestClient;
    private final int rateLimitWarnThreshold;

    public GitHubService(
            RestClient githubRestClient,
            @Value("${github.rate-limit-warn-threshold}") int rateLimitWarnThreshold
    ) {
        this.githubRestClient = githubRestClient;
        this.rateLimitWarnThreshold = rateLimitWarnThreshold;
    }

    public List<GitHubIssueSearchResponse.GitHubIssue> searchOpenIssuesByLabel(WatchedRepo watchedRepo) {
        // Comma-joins OR labels within one `label:` qualifier (GitHub search
        // syntax); this is NOT the same as repeating `label:` per value, which
        // ANDs them and would require an issue to carry every label at once.
        String labelClause = String.join(",", watchedRepo.getLabels().stream().map(l -> "\"" + l + "\"").toList());
        String query = "repo:%s/%s label:%s state:open is:issue".formatted(watchedRepo.getOwner(), watchedRepo.getRepo(), labelClause);

        ResponseEntity<GitHubIssueSearchResponse> response = executeWithRetry(() -> githubRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/issues")
                        .queryParam("q", query)
                        .queryParam("per_page", 100)
                        .build())
                .retrieve()
                .toEntity(GitHubIssueSearchResponse.class));

        logRateLimitIfLow(response);

        GitHubIssueSearchResponse body = response.getBody();
        return body == null ? List.of() : body.itemsOrEmpty();
    }

    /**
     * Generic issue search, not tied to a {@link WatchedRepo}.
     *
     * @param q       the fully-built GitHub search query (see {@code GitHubSearchQueryBuilder})
     * @param sort    {@code created}/{@code updated}/{@code comments}, or {@code null} for relevance
     * @param order   {@code asc}/{@code desc}; GitHub IGNORES it unless {@code sort} is also sent,
     *                so it is only attached when {@code sort} is non-null
     * @param page    1-based; caller must have already enforced page * perPage &lt;= 1000
     * @param perPage 1..100
     */
    public GitHubIssueSearchResponse searchIssues(String q, String sort, String order, int page, int perPage) {
        ResponseEntity<GitHubIssueSearchResponse> response = executeWithRetry(() -> githubRestClient.get()
                .uri(uriBuilder -> {
                    // NOTE: `advanced_search=true` used to be sent here. GitHub's REST
                    // reference now marks that parameter DEPRECATED — advanced search
                    // became the default on 2025-09-04 — so it is deliberately omitted.
                    // Please don't re-add it.
                    uriBuilder.path("/search/issues")
                            .queryParam("q", q)
                            .queryParam("per_page", perPage)
                            .queryParam("page", page);
                    if (sort != null) {
                        uriBuilder.queryParam("sort", sort);
                        if (order != null) {
                            uriBuilder.queryParam("order", order);
                        }
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .toEntity(GitHubIssueSearchResponse.class));

        logRateLimitIfLow(response);

        GitHubIssueSearchResponse body = response.getBody();
        return body == null ? new GitHubIssueSearchResponse(0, false, List.of()) : body;
    }

    /**
     * Generic GitHub search call, shared by all seven search types.
     *
     * <p>The query string is assembled and percent-encoded by hand rather than
     * through {@code UriBuilder.queryParam}: Spring treats {@code +} as a legal
     * query sub-delimiter and leaves it raw, but GitHub decodes a raw {@code +}
     * as a space — which would silently corrupt the {@code reactions-+1} sort into
     * {@code reactions- 1} and 422. Encoding here guarantees {@code %2B}.
     *
     * @param path        GitHub path, e.g. {@code /search/repositories}
     * @param q           the built search query; omitted when null/blank
     * @param sort        endpoint-specific sort value, or null for best match. Topics
     *                    supports no sorting, so null must genuinely send nothing.
     * @param order       {@code asc}/{@code desc}; only attached when {@code sort} is present,
     *                    because GitHub ignores it otherwise
     * @param extraParams endpoint-specific extras, e.g. {@code repository_id} for labels
     */
    public RawSearchResult search(String path, String q, String sort, String order,
                                  int page, int perPage, Map<String, String> extraParams) {
        Map<String, String> params = new LinkedHashMap<>();
        if (q != null && !q.isBlank()) {
            params.put("q", q);
        }
        if (extraParams != null) {
            extraParams.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    params.put(k, v);
                }
            });
        }
        if (sort != null) {
            params.put("sort", sort);
            if (order != null) {
                params.put("order", order);
            }
        }
        params.put("per_page", String.valueOf(perPage));
        params.put("page", String.valueOf(page));

        ResponseEntity<JsonNode> response = executeWithRetry(() -> githubRestClient.get()
                .uri(uriBuilder -> {
                    // build() resolves against the configured base URL, giving an
                    // absolute URI that RestClient then uses verbatim.
                    String base = uriBuilder.path(path).build().toString();
                    return URI.create(base + "?" + encodeQuery(params));
                })
                .retrieve()
                .toEntity(JsonNode.class));

        logRateLimitIfLow(response);
        return new RawSearchResult(response.getBody(), RateLimitInfo.from(response.getHeaders()));
    }

    /**
     * {@code GET /repos/{owner}/{repo}} — used only to turn an owner/repo pair into
     * the numeric {@code repository_id} that GitHub's label search requires.
     *
     * @return the repository JSON; never null on success
     */
    public JsonNode getRepository(String owner, String repo) {
        ResponseEntity<JsonNode> response = executeWithRetry(() -> githubRestClient.get()
                .uri(uriBuilder -> URI.create(uriBuilder.path("/repos").build().toString()
                        + "/" + encodeValue(owner) + "/" + encodeValue(repo)))
                .retrieve()
                .toEntity(JsonNode.class));

        logRateLimitIfLow(response);
        return response.getBody();
    }

    private static String encodeQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(encodeValue(key)).append('=').append(encodeValue(value));
        });
        return sb.toString();
    }

    /** {@code URLEncoder} is form encoding, which renders a space as {@code +}; queries need {@code %20}. */
    private static String encodeValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    <T> T executeWithRetry(Supplier<T> call) {
        Duration backoff = INITIAL_BACKOFF;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (ResourceAccessException | HttpServerErrorException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw ex;
                }
                log.warn("GitHub API call failed (attempt {}/{}), retrying in {}: {}",
                        attempt, MAX_ATTEMPTS, backoff, ex.getMessage());
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off GitHub API retry", e);
        }
    }

    private void logRateLimitIfLow(ResponseEntity<?> response) {
        String remainingHeader = response.getHeaders().getFirst("x-ratelimit-remaining");
        if (remainingHeader == null) {
            return;
        }
        int remaining;
        try {
            remaining = Integer.parseInt(remainingHeader.trim());
        } catch (NumberFormatException ex) {
            return;
        }

        // The `search` bucket is tiny and per-minute (10 unauthenticated / 30
        // authenticated), so the core-sized configured threshold would warn on
        // every single call. Scale the threshold to the bucket's own limit.
        int threshold = rateLimitWarnThreshold;
        String limitHeader = response.getHeaders().getFirst("x-ratelimit-limit");
        if (limitHeader != null) {
            try {
                int limit = Integer.parseInt(limitHeader.trim());
                threshold = Math.min(threshold, Math.max(1, limit / 4));
            } catch (NumberFormatException ignored) {
                // fall back to the configured threshold
            }
        }

        if (remaining < threshold) {
            String resetHeader = response.getHeaders().getFirst("x-ratelimit-reset");
            String resource = response.getHeaders().getFirst("x-ratelimit-resource");
            log.warn("GitHub API rate limit low ({} bucket): {} requests remaining, resets at epoch {}",
                    resource == null ? "core" : resource, remaining, resetHeader);
        }
    }
}
