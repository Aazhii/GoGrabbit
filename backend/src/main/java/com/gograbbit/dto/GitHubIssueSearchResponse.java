package com.gograbbit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueSearchResponse(
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("incomplete_results") boolean incompleteResults,
        List<GitHubIssue> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubIssue(
            long id,
            int number,
            String title,
            @JsonProperty("html_url") String htmlUrl,
            String state,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            List<Label> labels
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Label(String name) {
        }
    }
}
