package com.gograbbit.service;

import com.gograbbit.domain.SeenIssue;
import com.gograbbit.domain.WatchedRepo;
import com.gograbbit.dto.GitHubIssueSearchResponse.GitHubIssue;
import com.gograbbit.repository.SeenIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PollerService {

    private static final Logger log = LoggerFactory.getLogger(PollerService.class);

    private final GitHubService gitHubService;
    private final SeenIssueRepository seenIssueRepository;

    public PollerService(GitHubService gitHubService, SeenIssueRepository seenIssueRepository) {
        this.gitHubService = gitHubService;
        this.seenIssueRepository = seenIssueRepository;
    }

    @Transactional
    public int poll(WatchedRepo watchedRepo) {
        List<GitHubIssue> issues = gitHubService.searchOpenIssuesByLabel(watchedRepo);
        int newCount = 0;

        for (GitHubIssue issue : issues) {
            if (seenIssueRepository.existsByGithubIssueIdAndWatchedRepo(issue.id(), watchedRepo)) {
                continue;
            }

            SeenIssue seenIssue = new SeenIssue(
                    issue.id(), watchedRepo, issue.title(), issue.htmlUrl(), issue.updatedAt(), issue.createdAt());
            seenIssueRepository.save(seenIssue);
            newCount++;

            // Notification dispatch (Email/Telegram/Discord) lands in the next phase.
            log.info("New {} issue in {}/{}: {} ({})",
                    String.join("|", watchedRepo.getLabels()), watchedRepo.getOwner(), watchedRepo.getRepo(),
                    issue.title(), issue.htmlUrl());
        }

        return newCount;
    }
}
