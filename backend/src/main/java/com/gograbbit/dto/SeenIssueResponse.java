package com.gograbbit.dto;

import com.gograbbit.domain.SeenIssue;

import java.time.Instant;
import java.util.UUID;

public record SeenIssueResponse(
        UUID id,
        String owner,
        String repo,
        String title,
        String url,
        Instant postedAt,
        Instant labeledAt,
        Instant notifiedAt
) {
    public static SeenIssueResponse from(SeenIssue seenIssue) {
        return new SeenIssueResponse(
                seenIssue.getId(),
                seenIssue.getWatchedRepo().getOwner(),
                seenIssue.getWatchedRepo().getRepo(),
                seenIssue.getTitle(),
                seenIssue.getUrl(),
                seenIssue.getPostedAt(),
                seenIssue.getLabeledAt(),
                seenIssue.getNotifiedAt()
        );
    }
}
