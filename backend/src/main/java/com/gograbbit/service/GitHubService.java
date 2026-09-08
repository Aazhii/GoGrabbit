package com.gograbbit.service;

import com.gograbbit.domain.WatchedRepo;
import com.gograbbit.dto.GitHubIssueSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
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
                    uriBuilder.path("/search/issues")
                            .queryParam("q", q)
                            // Advanced search became the default on 2025-09-04; sending it
                            // explicitly pins the semantics regardless of future defaults.
                            .queryParam("advanced_search", "true")
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
