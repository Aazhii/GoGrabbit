package com.gograbbit.dto;

import com.gograbbit.domain.WatchedRepo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WatchedRepoResponse(
        UUID id,
        String owner,
        String repo,
        List<String> labels,
        int intervalMinutes,
        boolean active,
        Instant createdAt
) {
    public static WatchedRepoResponse from(WatchedRepo watchedRepo) {
        return new WatchedRepoResponse(
                watchedRepo.getId(),
                watchedRepo.getOwner(),
                watchedRepo.getRepo(),
                watchedRepo.getLabels(),
                watchedRepo.getIntervalMinutes(),
                watchedRepo.isActive(),
                watchedRepo.getCreatedAt()
        );
    }
}
