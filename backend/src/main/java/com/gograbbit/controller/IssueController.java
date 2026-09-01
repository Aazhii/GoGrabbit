package com.gograbbit.controller;

import com.gograbbit.dto.SeenIssueResponse;
import com.gograbbit.repository.SeenIssueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
public class IssueController {

    private final SeenIssueRepository seenIssueRepository;

    public IssueController(SeenIssueRepository seenIssueRepository) {
        this.seenIssueRepository = seenIssueRepository;
    }

    @GetMapping("/issues/recent")
    public List<SeenIssueResponse> recent(
            @RequestParam(required = false) Integer sinceDays,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(defaultValue = "50") int limit
    ) {
        Instant since = sinceDays == null ? null : Instant.now().minus(sinceDays, ChronoUnit.DAYS);

        return seenIssueRepository.search(since, owner, repo, PageRequest.of(0, limit))
                .map(SeenIssueResponse::from)
                .getContent();
    }
}
