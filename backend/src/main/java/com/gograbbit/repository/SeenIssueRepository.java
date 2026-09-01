package com.gograbbit.repository;

import com.gograbbit.domain.SeenIssue;
import com.gograbbit.domain.WatchedRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface SeenIssueRepository extends JpaRepository<SeenIssue, UUID> {

    boolean existsByGithubIssueIdAndWatchedRepo(long githubIssueId, WatchedRepo watchedRepo);

    @Query("""
            select s from SeenIssue s join fetch s.watchedRepo wr
            where (cast(:since as timestamp) is null or s.postedAt >= :since)
            and (cast(:owner as string) is null or wr.owner = :owner)
            and (cast(:repo as string) is null or wr.repo = :repo)
            order by s.postedAt desc
            """)
    Page<SeenIssue> search(
            @Param("since") Instant since,
            @Param("owner") String owner,
            @Param("repo") String repo,
            Pageable pageable
    );
}
