package com.gograbbit.repository;

import com.gograbbit.domain.SeenIssue;
import com.gograbbit.domain.WatchedRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface SeenIssueRepository extends JpaRepository<SeenIssue, UUID> {

    boolean existsByGithubIssueIdAndWatchedRepo(long githubIssueId, WatchedRepo watchedRepo);

    @Query("select s from SeenIssue s join fetch s.watchedRepo order by s.notifiedAt desc")
    Page<SeenIssue> findAllByOrderByNotifiedAtDesc(Pageable pageable);
}
