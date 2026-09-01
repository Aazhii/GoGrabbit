package com.gograbbit.service;

import com.gograbbit.domain.WatchedRepo;
import com.gograbbit.dto.GitHubIssueSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

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
        String labelClause = String.join(",", watchedRepo.getLabels().stream().map(l -> "\"" + l + "\"").toList());
        String query = "repo:%s/%s label:%s state:open is:issue".formatted(watchedRepo.getOwner(), watchedRepo.getRepo(), labelClause);

        ResponseEntity<GitHubIssueSearchResponse> response = githubRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/issues")
                        .queryParam("q", query)
                        .queryParam("per_page", 100)
                        .build())
                .retrieve()
                .toEntity(GitHubIssueSearchResponse.class);

        logRateLimitIfLow(response);

        GitHubIssueSearchResponse body = response.getBody();
        return body == null ? List.of() : body.items();
    }

    private void logRateLimitIfLow(ResponseEntity<?> response) {
        String remainingHeader = response.getHeaders().getFirst("x-ratelimit-remaining");
        if (remainingHeader == null) {
            return;
        }
        int remaining = Integer.parseInt(remainingHeader);
        if (remaining < rateLimitWarnThreshold) {
            String resetHeader = response.getHeaders().getFirst("x-ratelimit-reset");
            log.warn("GitHub API rate limit low: {} requests remaining, resets at epoch {}", remaining, resetHeader);
        }
    }
}
