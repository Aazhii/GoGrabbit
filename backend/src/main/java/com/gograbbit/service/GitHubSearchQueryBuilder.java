package com.gograbbit.service;

import com.gograbbit.dto.IssueSearchCriteria;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure translation of {@link IssueSearchCriteria} into GitHub's search {@code q}
 * string. No HTTP, no state beyond an injectable {@link Clock} (so
 * {@code createdWithinDays} is testable).
 *
 * <p>Two GitHub search-syntax rules this encodes deliberately:
 * <ul>
 *   <li>Values are comma-joined inside ONE {@code label:} qualifier — that's OR.
 *       Repeating {@code label:} would AND them.</li>
 *   <li>Date qualifiers always carry an explicit full UTC timestamp
 *       ({@code created:>=2026-09-01T00:00:00Z}). Bare {@code YYYY-MM-DD} dates
 *       have undocumented timezone handling.</li>
 * </ul>
 */
@Component
public class GitHubSearchQueryBuilder {

    private static final DateTimeFormatter UTC_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final Clock clock;

    public GitHubSearchQueryBuilder() {
        this(Clock.systemUTC());
    }

    public GitHubSearchQueryBuilder(Clock clock) {
        this.clock = clock;
    }

    public String build(IssueSearchCriteria criteria) {
        List<String> parts = new ArrayList<>();

        if (criteria.q() != null && !criteria.q().isBlank()) {
            parts.add(criteria.q().trim());
        }
        if (criteria.owner() != null && criteria.repo() != null) {
            parts.add("repo:" + criteria.owner() + "/" + criteria.repo());
        } else if (criteria.owner() != null) {
            parts.add("user:" + criteria.owner());
        }
        if (!criteria.labels().isEmpty()) {
            String joined = String.join(",", criteria.labels().stream().map(GitHubSearchQueryBuilder::quote).toList());
            parts.add("label:" + joined);
        }

        // Always pin to issues: every pull request is also an issue in the search index.
        parts.add("is:issue");

        switch (criteria.state()) {
            case OPEN -> parts.add("is:open");
            case CLOSED -> parts.add("is:closed");
            case ALL -> { /* no state qualifier — both open and closed */ }
        }

        String created = createdQualifier(criteria);
        if (created != null) {
            parts.add(created);
        }

        return String.join(" ", parts);
    }

    private String createdQualifier(IssueSearchCriteria criteria) {
        if (criteria.createdWithinDays() != null) {
            Instant since = Instant.now(clock).minus(criteria.createdWithinDays(), ChronoUnit.DAYS);
            return "created:>=" + UTC_TIMESTAMP.format(since);
        }

        LocalDate from = criteria.createdFrom();
        LocalDate to = criteria.createdTo();
        if (from != null && to != null) {
            return "created:" + startOfDay(from) + ".." + endOfDay(to);
        }
        if (from != null) {
            return "created:>=" + startOfDay(from);
        }
        if (to != null) {
            return "created:<=" + endOfDay(to);
        }
        return null;
    }

    private static String startOfDay(LocalDate date) {
        return UTC_TIMESTAMP.format(date.atStartOfDay(ZoneOffset.UTC));
    }

    private static String endOfDay(LocalDate date) {
        return UTC_TIMESTAMP.format(date.atTime(23, 59, 59).atZone(ZoneOffset.UTC));
    }

    /** Label values may contain spaces ("good first issue"), so always quote them. */
    private static String quote(String label) {
        return "\"" + label.replace("\"", "") + "\"";
    }
}
