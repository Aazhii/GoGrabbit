package com.gograbbit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Validated inputs for {@code GET /issues/search}.
 *
 * <p>Validation lives here (in {@link #of}) rather than in bean-validation
 * annotations on the controller: every rule below needs a specific, human
 * readable 400 message (mutually exclusive date filters, the GitHub 1000-result
 * reachable cap, repo-without-owner), and cross-field rules don't express well
 * as parameter annotations. The annotations on the components are kept as
 * documentation of the accepted ranges and stay in sync with {@link #of}.
 */
public record IssueSearchCriteria(
        String q,
        List<String> labels,
        @NotNull State state,
        String owner,
        String repo,
        Integer createdWithinDays,
        LocalDate createdFrom,
        LocalDate createdTo,
        @NotNull Sort sort,
        @NotNull Order order,
        @Min(1) int page,
        @Min(1) @Max(100) int perPage
) {

    /**
     * GitHub's Search API can only page to the first 1000 results; asking for
     * anything past that is a 422 from GitHub, so we reject it as a 400 here.
     */
    public static final int MAX_REACHABLE_RESULTS = 1000;
    public static final int MAX_PER_PAGE = 100;
    public static final int DEFAULT_PER_PAGE = 30;

    public enum State {
        OPEN, CLOSED, ALL;

        static State parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return OPEN;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "open" -> OPEN;
                case "closed" -> CLOSED;
                case "all" -> ALL;
                default -> throw new IllegalArgumentException(
                        "Invalid 'state': '" + raw + "'. Expected one of: open, closed, all.");
            };
        }
    }

    /** {@code BEST_MATCH} means: omit both {@code sort} and {@code order} from the GitHub call. */
    public enum Sort {
        CREATED("created"), UPDATED("updated"), COMMENTS("comments"), BEST_MATCH(null);

        private final String githubValue;

        Sort(String githubValue) {
            this.githubValue = githubValue;
        }

        /** The value to send as GitHub's {@code sort} param, or {@code null} for relevance ordering. */
        public String githubValue() {
            return githubValue;
        }

        static Sort parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return CREATED;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "created" -> CREATED;
                case "updated" -> UPDATED;
                case "comments" -> COMMENTS;
                case "bestmatch" -> BEST_MATCH;
                default -> throw new IllegalArgumentException(
                        "Invalid 'sort': '" + raw + "'. Expected one of: created, updated, comments, bestmatch.");
            };
        }
    }

    public enum Order {
        ASC("asc"), DESC("desc");

        private final String githubValue;

        Order(String githubValue) {
            this.githubValue = githubValue;
        }

        public String githubValue() {
            return githubValue;
        }

        static Order parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return DESC;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "asc" -> ASC;
                case "desc" -> DESC;
                default -> throw new IllegalArgumentException(
                        "Invalid 'order': '" + raw + "'. Expected one of: asc, desc.");
            };
        }
    }

    public IssueSearchCriteria {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    /**
     * Parses and validates raw request parameters.
     *
     * @throws IllegalArgumentException with a user-facing message on any invalid
     *                                  or mutually-exclusive combination; the
     *                                  global handler maps that to HTTP 400.
     */
    public static IssueSearchCriteria of(
            String q,
            String labels,
            String state,
            String owner,
            String repo,
            Integer createdWithinDays,
            String createdFrom,
            String createdTo,
            String sort,
            String order,
            Integer page,
            Integer perPage
    ) {
        String trimmedOwner = trimToNull(owner);
        String trimmedRepo = trimToNull(repo);
        if (trimmedRepo != null && trimmedOwner == null) {
            throw new IllegalArgumentException(
                    "'repo' requires 'owner' — GitHub search scopes a repository as owner/repo, "
                            + "so pass both (e.g. owner=spotify&repo=backstage).");
        }

        LocalDate from = parseDate(createdFrom, "createdFrom");
        LocalDate to = parseDate(createdTo, "createdTo");

        if (createdWithinDays != null && (from != null || to != null)) {
            throw new IllegalArgumentException(
                    "'createdWithinDays' is mutually exclusive with 'createdFrom'/'createdTo' — pass one or the other.");
        }
        if (createdWithinDays != null && createdWithinDays < 1) {
            throw new IllegalArgumentException("'createdWithinDays' must be a positive integer.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'createdFrom' must not be after 'createdTo'.");
        }

        int resolvedPage = page == null ? 1 : page;
        int resolvedPerPage = perPage == null ? DEFAULT_PER_PAGE : perPage;
        if (resolvedPage < 1) {
            throw new IllegalArgumentException("'page' must be at least 1.");
        }
        if (resolvedPerPage < 1 || resolvedPerPage > MAX_PER_PAGE) {
            throw new IllegalArgumentException("'perPage' must be between 1 and " + MAX_PER_PAGE + ".");
        }
        if ((long) resolvedPage * resolvedPerPage > MAX_REACHABLE_RESULTS) {
            throw new IllegalArgumentException(
                    "GitHub search only returns the first " + MAX_REACHABLE_RESULTS + " results, so page * perPage "
                            + "must not exceed " + MAX_REACHABLE_RESULTS + " (requested page=" + resolvedPage
                            + ", perPage=" + resolvedPerPage + "). Narrow the query instead of paging further.");
        }

        return new IssueSearchCriteria(
                trimToNull(q),
                parseLabels(labels),
                State.parse(state),
                trimmedOwner,
                trimmedRepo,
                createdWithinDays,
                from,
                to,
                Sort.parse(sort),
                Order.parse(order),
                resolvedPage,
                resolvedPerPage
        );
    }

    private static List<String> parseLabels(String labels) {
        if (labels == null || labels.isBlank()) {
            return List.of();
        }
        List<String> parsed = java.util.Arrays.stream(labels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return List.copyOf(parsed);
    }

    private static LocalDate parseDate(String raw, String field) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Invalid '" + field + "': '" + raw + "'. Expected an ISO date, YYYY-MM-DD.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
