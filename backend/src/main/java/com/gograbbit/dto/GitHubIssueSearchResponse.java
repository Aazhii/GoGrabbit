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

    public List<GitHubIssue> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubIssue(
            long id,
            int number,
            String title,
            @JsonProperty("html_url") String htmlUrl,
            String state,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            List<Label> labels,
            int comments,
            User user,
            List<User> assignees,
            @JsonProperty("repository_url") String repositoryUrl,
            // Present (non-null) only when the item is actually a pull request.
            // Every PR is also an issue in GitHub's search index, so this is the
            // only reliable discriminator.
            @JsonProperty("pull_request") PullRequestRef pullRequest
    ) {
        public boolean isPullRequest() {
            return pullRequest != null;
        }

        public List<Label> labelsOrEmpty() {
            return labels == null ? List.of() : labels;
        }

        public List<User> assigneesOrEmpty() {
            return assignees == null ? List.of() : assignees;
        }

        /**
         * {@code repository_url} is {@code https://api.github.com/repos/OWNER/REPO}.
         *
         * @return {@code [owner, repo]}, or {@code null} if the URL is absent/unparseable.
         */
        public String[] ownerAndRepo() {
            if (repositoryUrl == null) {
                return null;
            }
            int marker = repositoryUrl.indexOf("/repos/");
            if (marker < 0) {
                return null;
            }
            String[] parts = repositoryUrl.substring(marker + "/repos/".length()).split("/");
            if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return null;
            }
            return new String[]{parts[0], parts[1]};
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Label(String name, String color) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record User(
                String login,
                @JsonProperty("avatar_url") String avatarUrl,
                @JsonProperty("html_url") String htmlUrl
        ) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PullRequestRef(@JsonProperty("html_url") String htmlUrl) {
        }
    }
}
