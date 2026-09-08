package com.gograbbit.service;

import com.gograbbit.dto.GitHubIssueSearchResponse;
import com.gograbbit.dto.GitHubIssueSearchResponse.GitHubIssue;
import com.gograbbit.dto.IssueSearchCriteria;
import com.gograbbit.dto.IssueSearchResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure mapping tests — no Spring context, no HTTP, no Postgres. The GitHub
 * response DTO is constructed directly.
 */
class IssueSearchServiceTest {

    private final IssueSearchService service = new IssueSearchService(null, new GitHubSearchQueryBuilder());

    private static IssueSearchCriteria criteria(int page, int perPage) {
        return IssueSearchCriteria.of(null, null, null, null, null, null, null, null, null, null, page, perPage);
    }

    private static GitHubIssue issue(long id, GitHubIssue.PullRequestRef pr) {
        return new GitHubIssue(
                id, (int) id, "issue " + id, "https://github.com/spotify/backstage/issues/" + id, "open",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-02T10:00:00Z"),
                List.of(new GitHubIssue.Label("good first issue", "7057ff")),
                3,
                new GitHubIssue.User("octocat", "https://avatars/1", "https://github.com/octocat"),
                List.of(new GitHubIssue.User("hubot", "https://avatars/2", "https://github.com/hubot")),
                "https://api.github.com/repos/spotify/backstage",
                pr);
    }

    @Test
    void pullRequestsAreFilteredOut() {
        GitHubIssueSearchResponse raw = new GitHubIssueSearchResponse(3, false, List.of(
                issue(1, null),
                issue(2, new GitHubIssue.PullRequestRef("https://github.com/spotify/backstage/pull/2")),
                issue(3, null)));

        IssueSearchResponse response = service.toResponse(criteria(1, 30), "is:issue is:open", raw);

        assertThat(response.items()).extracting(IssueSearchResponse.Item::id).containsExactly(1L, 3L);
    }

    @Test
    void mapsEveryContractFieldOfAnItem() {
        GitHubIssueSearchResponse raw = new GitHubIssueSearchResponse(1, false, List.of(issue(45, null)));

        IssueSearchResponse.Item item = service.toResponse(criteria(1, 30), "q", raw).items().getFirst();

        assertThat(item.number()).isEqualTo(45);
        assertThat(item.title()).isEqualTo("issue 45");
        assertThat(item.url()).isEqualTo("https://github.com/spotify/backstage/issues/45");
        assertThat(item.state()).isEqualTo("open");
        assertThat(item.owner()).isEqualTo("spotify");
        assertThat(item.repo()).isEqualTo("backstage");
        assertThat(item.repositoryFullName()).isEqualTo("spotify/backstage");
        assertThat(item.labels()).containsExactly(new IssueSearchResponse.Label("good first issue", "7057ff"));
        assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
        assertThat(item.updatedAt()).isEqualTo(Instant.parse("2026-09-02T10:00:00Z"));
        assertThat(item.comments()).isEqualTo(3);
        assertThat(item.author().login()).isEqualTo("octocat");
        assertThat(item.assignees()).extracting(IssueSearchResponse.User::login).containsExactly("hubot");
    }

    @Test
    void ghostAuthorAndMissingAssigneesAreTolerated() {
        GitHubIssue ghost = new GitHubIssue(
                9, 9, "t", "u", "open", Instant.EPOCH, Instant.EPOCH,
                null, 0, null, null, "https://api.github.com/repos/o/r", null);

        IssueSearchResponse.Item item = service
                .toResponse(criteria(1, 30), "q", new GitHubIssueSearchResponse(1, false, List.of(ghost)))
                .items().getFirst();

        assertThat(item.author()).isNull();
        assertThat(item.assignees()).isEmpty();
        assertThat(item.labels()).isEmpty();
    }

    @Test
    void unparseableRepositoryUrlLeavesOwnerAndRepoNull() {
        GitHubIssue odd = new GitHubIssue(
                9, 9, "t", "u", "open", Instant.EPOCH, Instant.EPOCH,
                List.of(), 0, null, List.of(), null, null);

        IssueSearchResponse.Item item = service
                .toResponse(criteria(1, 30), "q", new GitHubIssueSearchResponse(1, false, List.of(odd)))
                .items().getFirst();

        assertThat(item.owner()).isNull();
        assertThat(item.repo()).isNull();
        assertThat(item.repositoryFullName()).isNull();
    }

    @Test
    void resultsCappedWhenTotalCountExceedsOneThousand() {
        IssueSearchResponse response = service.toResponse(
                criteria(1, 30), "q", new GitHubIssueSearchResponse(54321, false, List.of(issue(1, null))));

        assertThat(response.totalCount()).isEqualTo(54321);
        assertThat(response.resultsCapped()).isTrue();
        assertThat(response.hasNextPage()).isTrue();
    }

    @Test
    void noNextPageOnceTheThousandResultWindowIsExhausted() {
        IssueSearchResponse response = service.toResponse(
                criteria(10, 100), "q", new GitHubIssueSearchResponse(54321, false, List.of(issue(1, null))));

        assertThat(response.resultsCapped()).isTrue();
        assertThat(response.hasNextPage()).isFalse();
    }

    @Test
    void noNextPageOnTheLastPartialPage() {
        IssueSearchResponse response = service.toResponse(
                criteria(2, 30), "q", new GitHubIssueSearchResponse(45, false, List.of(issue(1, null))));

        assertThat(response.resultsCapped()).isFalse();
        assertThat(response.hasNextPage()).isFalse();
    }

    @Test
    void incompleteResultsAndQueryArePassedThrough() {
        IssueSearchResponse response = service.toResponse(
                criteria(1, 30), "label:\"good first issue\" is:issue is:open",
                new GitHubIssueSearchResponse(1, true, List.of()));

        assertThat(response.incompleteResults()).isTrue();
        assertThat(response.query()).isEqualTo("label:\"good first issue\" is:issue is:open");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.perPage()).isEqualTo(30);
    }

    @Test
    void nullItemsFromGitHubYieldAnEmptyList() {
        IssueSearchResponse response = service.toResponse(
                criteria(1, 30), "q", new GitHubIssueSearchResponse(0, false, null));

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNextPage()).isFalse();
    }
}
