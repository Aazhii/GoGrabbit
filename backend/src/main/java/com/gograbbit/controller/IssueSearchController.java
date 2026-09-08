package com.gograbbit.controller;

import com.gograbbit.dto.IssueSearchCriteria;
import com.gograbbit.dto.IssueSearchResponse;
import com.gograbbit.service.IssueSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issue-first discovery: search GitHub directly, without a repo having to be
 * watched first. Read-only and stateless — nothing here touches {@code seen_issue}.
 *
 * <p>All parameter validation happens in {@link IssueSearchCriteria#of}, which
 * throws {@link IllegalArgumentException}; {@code GlobalExceptionHandler} maps
 * that to a 400 carrying the message.
 */
@RestController
public class IssueSearchController {

    private final IssueSearchService issueSearchService;

    public IssueSearchController(IssueSearchService issueSearchService) {
        this.issueSearchService = issueSearchService;
    }

    @GetMapping("/issues/search")
    public IssueSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String labels,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) Integer createdWithinDays,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage
    ) {
        IssueSearchCriteria criteria = IssueSearchCriteria.of(
                q, labels, state, owner, repo,
                createdWithinDays, createdFrom, createdTo,
                sort, order, page, perPage);

        return issueSearchService.search(criteria);
    }
}
