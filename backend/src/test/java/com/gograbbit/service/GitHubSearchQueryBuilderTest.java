package com.gograbbit.service;

import com.gograbbit.dto.IssueSearchCriteria;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubSearchQueryBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:34:56Z");

    private final GitHubSearchQueryBuilder builder =
            new GitHubSearchQueryBuilder(Clock.fixed(NOW, ZoneOffset.UTC));

    private String build(String q, String labels, String state, String owner, String repo,
                         Integer withinDays, String from, String to, String sort, String order) {
        return builder.build(IssueSearchCriteria.of(
                q, labels, state, owner, repo, withinDays, from, to, sort, order, null, null));
    }

    @Test
    void defaultsToOpenIssues() {
        String q = build(null, null, null, null, null, null, null, null, null, null);

        assertThat(q).contains("is:issue");
        assertThat(q).contains("is:open");
        assertThat(q).doesNotContain("is:closed");
    }

    @Test
    void singleLabelIsQuoted() {
        String q = build(null, "bug", null, null, null, null, null, null, null, null);

        assertThat(q).contains("label:\"bug\"");
    }

    @Test
    void multiWordLabelIsQuoted() {
        String q = build(null, "good first issue", null, null, null, null, null, null, null, null);

        assertThat(q).contains("label:\"good first issue\"");
    }

    @Test
    void multipleLabelsAreCommaJoinedInOneQualifier() {
        String q = build(null, "good first issue, help wanted ,bug", null, null, null,
                null, null, null, null, null);

        // ONE label: qualifier, comma-joined => OR semantics. Repeating label: would AND.
        assertThat(q).contains("label:\"good first issue\",\"help wanted\",\"bug\"");
        assertThat(q.split("label:", -1)).hasSize(2);
    }

    @Test
    void ownerAndRepoBecomeRepoQualifier() {
        String q = build(null, null, null, "spotify", "backstage", null, null, null, null, null);

        assertThat(q).contains("repo:spotify/backstage");
    }

    @Test
    void ownerAloneScopesToThatOwner() {
        String q = build(null, null, null, "spotify", null, null, null, null, null, null);

        assertThat(q).contains("user:spotify");
        assertThat(q).doesNotContain("repo:");
    }

    @Test
    void repoWithoutOwnerIsRejected() {
        assertThatThrownBy(() -> build(null, null, null, null, "backstage", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'repo' requires 'owner'");
    }

    @Test
    void createdWithinDaysBecomesFullUtcTimestamp() {
        String q = build(null, null, null, null, null, 7, null, null, null, null);

        // NOW - 7d = 2026-09-01T12:34:56Z — a full explicit UTC timestamp, never a bare date.
        assertThat(q).contains("created:>=2026-09-01T12:34:56Z");
    }

    @Test
    void createdFromAndToBecomeAUtcRange() {
        String q = build(null, null, null, null, null, null, "2026-09-01", "2026-09-05", null, null);

        assertThat(q).contains("created:2026-09-01T00:00:00Z..2026-09-05T23:59:59Z");
    }

    @Test
    void createdFromOnlyBecomesLowerBound() {
        String q = build(null, null, null, null, null, null, "2026-09-01", null, null, null);

        assertThat(q).contains("created:>=2026-09-01T00:00:00Z");
    }

    @Test
    void createdToOnlyBecomesUpperBound() {
        String q = build(null, null, null, null, null, null, null, "2026-09-05", null, null);

        assertThat(q).contains("created:<=2026-09-05T23:59:59Z");
    }

    @Test
    void createdWithinDaysIsMutuallyExclusiveWithExplicitRange() {
        assertThatThrownBy(() -> build(null, null, null, null, null, 7, "2026-09-01", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void stateAllEmitsNeitherOpenNorClosed() {
        String q = build(null, null, "all", null, null, null, null, null, null, null);

        assertThat(q).contains("is:issue");
        assertThat(q).doesNotContain("is:open");
        assertThat(q).doesNotContain("is:closed");
    }

    @Test
    void stateClosedEmitsIsClosed() {
        String q = build(null, null, "closed", null, null, null, null, null, null, null);

        assertThat(q).contains("is:closed");
        assertThat(q).doesNotContain("is:open");
    }

    @Test
    void freeTextIsIncluded() {
        String q = build("memory leak", "bug", null, null, null, null, null, null, null, null);

        assertThat(q).startsWith("memory leak ");
        assertThat(q).contains("label:\"bug\"");
    }

    @Test
    void fullCombinationRendersEveryQualifier() {
        String q = build("crash", "good first issue", "open", "spotify", "backstage",
                null, "2026-09-01", "2026-09-05", null, null);

        assertThat(q).isEqualTo("crash repo:spotify/backstage label:\"good first issue\" is:issue is:open "
                + "created:2026-09-01T00:00:00Z..2026-09-05T23:59:59Z");
    }

    @Test
    void bestMatchOmitsSortAndOrder() {
        IssueSearchCriteria c = IssueSearchCriteria.of(
                null, null, null, null, null, null, null, null, "bestmatch", "asc", null, null);

        assertThat(c.sort().githubValue()).isNull();
    }

    @Test
    void defaultSortIsCreatedDesc() {
        IssueSearchCriteria c = IssueSearchCriteria.of(
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(c.sort().githubValue()).isEqualTo("created");
        assertThat(c.order().githubValue()).isEqualTo("desc");
    }

    @Test
    void invalidSortIsRejected() {
        assertThatThrownBy(() -> IssueSearchCriteria.of(
                null, null, null, null, null, null, null, null, "reactions", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid 'sort'");
    }

    @Test
    void invalidStateIsRejected() {
        assertThatThrownBy(() -> build(null, null, "banana", null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid 'state'");
    }

    @Test
    void paginationBeyondTheThousandResultCapIsRejected() {
        // page 11 * 100 = 1100 > 1000 — GitHub would answer 422; we answer 400 first.
        assertThatThrownBy(() -> IssueSearchCriteria.of(
                null, null, null, null, null, null, null, null, null, null, 11, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
    }

    @Test
    void theLastReachablePageIsAccepted() {
        IssueSearchCriteria c = IssueSearchCriteria.of(
                null, null, null, null, null, null, null, null, null, null, 10, 100);

        assertThat(c.page()).isEqualTo(10);
        assertThat(c.perPage()).isEqualTo(100);
    }

    @Test
    void perPageAboveOneHundredIsRejected() {
        assertThatThrownBy(() -> IssueSearchCriteria.of(
                null, null, null, null, null, null, null, null, null, null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("perPage");
    }

    @Test
    void pageBelowOneIsRejected() {
        assertThatThrownBy(() -> IssueSearchCriteria.of(
                null, null, null, null, null, null, null, null, null, null, 0, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void malformedDateIsRejected() {
        assertThatThrownBy(() -> build(null, null, null, null, null, null, "01-09-2026", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdFrom");
    }
}
