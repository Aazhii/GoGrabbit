package com.gograbbit.service;

import com.gograbbit.dto.GitHubIssueSearchResponse;
import com.gograbbit.dto.GitHubIssueSearchResponse.GitHubIssue;
import com.gograbbit.dto.IssueSearchCriteria;
import com.gograbbit.dto.IssueSearchResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates issue-first discovery: criteria → GitHub {@code q} → GitHub call →
 * PR filtering → wire response.
 */
@Service
public class IssueSearchService {

    private final GitHubService gitHubService;
    private final GitHubSearchQueryBuilder queryBuilder;

    public IssueSearchService(GitHubService gitHubService, GitHubSearchQueryBuilder queryBuilder) {
        this.gitHubService = gitHubService;
        this.queryBuilder = queryBuilder;
    }

    public IssueSearchResponse search(IssueSearchCriteria criteria) {
        String query = queryBuilder.build(criteria);
        String sort = criteria.sort().githubValue();
        // GitHub ignores `order` unless `sort` is present; for best-match we send neither.
        String order = sort == null ? null : criteria.order().githubValue();

        GitHubIssueSearchResponse raw =
                gitHubService.searchIssues(query, sort, order, criteria.page(), criteria.perPage());

        return toResponse(criteria, query, raw);
    }

    /** Package-private so it can be unit-tested without any HTTP. */
    IssueSearchResponse toResponse(IssueSearchCriteria criteria, String query, GitHubIssueSearchResponse raw) {
        List<IssueSearchResponse.Item> items = raw.itemsOrEmpty().stream()
                // `is:issue` should already exclude PRs, but the search index treats
                // every PR as an issue; a non-null `pull_request` is the real check.
                .filter(item -> !item.isPullRequest())
                .map(IssueSearchService::toItem)
                .toList();

        int totalCount = raw.totalCount();
        boolean resultsCapped = totalCount > IssueSearchCriteria.MAX_REACHABLE_RESULTS;
        int reachable = Math.min(totalCount, IssueSearchCriteria.MAX_REACHABLE_RESULTS);

        long consumed = (long) criteria.page() * criteria.perPage();
        // A next page exists only if there are more reachable results AND that next
        // page still starts inside the 1000-result window GitHub will serve.
        boolean hasNextPage = consumed < reachable
                && consumed + criteria.perPage() <= IssueSearchCriteria.MAX_REACHABLE_RESULTS;

        return new IssueSearchResponse(
                items,
                totalCount,
                criteria.page(),
                criteria.perPage(),
                hasNextPage,
                resultsCapped,
                raw.incompleteResults(),
                query
        );
    }

    private static IssueSearchResponse.Item toItem(GitHubIssue issue) {
        String[] ownerRepo = issue.ownerAndRepo();
        String owner = ownerRepo == null ? null : ownerRepo[0];
        String repo = ownerRepo == null ? null : ownerRepo[1];
        String fullName = ownerRepo == null ? null : owner + "/" + repo;

        return new IssueSearchResponse.Item(
                issue.id(),
                issue.number(),
                issue.title(),
                issue.htmlUrl(),
                issue.state(),
                owner,
                repo,
                fullName,
                issue.labelsOrEmpty().stream()
                        .map(l -> new IssueSearchResponse.Label(l.name(), l.color()))
                        .toList(),
                issue.createdAt(),
                issue.updatedAt(),
                issue.comments(),
                toUser(issue.user()),
                issue.assigneesOrEmpty().stream().map(IssueSearchService::toUser).toList()
        );
    }

    /** @return null for ghost (deleted) users, which GitHub reports as a null {@code user}. */
    private static IssueSearchResponse.User toUser(GitHubIssue.User user) {
        return user == null ? null : new IssueSearchResponse.User(user.login(), user.avatarUrl(), user.htmlUrl());
    }
}
