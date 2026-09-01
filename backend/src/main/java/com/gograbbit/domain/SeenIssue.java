package com.gograbbit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "seen_issue",
        uniqueConstraints = @UniqueConstraint(columnNames = {"github_issue_id", "watched_repo_id"})
)
public class SeenIssue {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "github_issue_id", nullable = false)
    private long githubIssueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watched_repo_id", nullable = false)
    private WatchedRepo watchedRepo;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String url;

    @Column(name = "labeled_at", nullable = false)
    private Instant labeledAt;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt = Instant.now();

    protected SeenIssue() {
    }

    public SeenIssue(long githubIssueId, WatchedRepo watchedRepo, String title, String url, Instant labeledAt, Instant postedAt) {
        this.githubIssueId = githubIssueId;
        this.watchedRepo = watchedRepo;
        this.title = title;
        this.url = url;
        this.labeledAt = labeledAt;
        this.postedAt = postedAt;
    }

    public UUID getId() {
        return id;
    }

    public long getGithubIssueId() {
        return githubIssueId;
    }

    public WatchedRepo getWatchedRepo() {
        return watchedRepo;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public Instant getLabeledAt() {
        return labeledAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }
}
