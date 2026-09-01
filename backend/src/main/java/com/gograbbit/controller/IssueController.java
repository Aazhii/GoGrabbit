package com.gograbbit.controller;

import com.gograbbit.dto.SeenIssueResponse;
import com.gograbbit.repository.SeenIssueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class IssueController {

    private final SeenIssueRepository seenIssueRepository;

    public IssueController(SeenIssueRepository seenIssueRepository) {
        this.seenIssueRepository = seenIssueRepository;
    }

    @GetMapping("/issues/recent")
    public List<SeenIssueResponse> recent(@RequestParam(defaultValue = "50") int limit) {
        return seenIssueRepository.findAllByOrderByNotifiedAtDesc(PageRequest.of(0, limit))
                .map(SeenIssueResponse::from)
                .getContent();
    }
}
