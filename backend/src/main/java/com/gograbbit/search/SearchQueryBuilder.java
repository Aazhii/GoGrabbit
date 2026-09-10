package com.gograbbit.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic, spec-driven translation of raw request parameters into a GitHub search
 * {@code q} string. Pure: no HTTP, no clock, no state.
 *
 * <h2>Conventions this encodes</h2>
 * <ul>
 *   <li><b>Unknown keys are a hard error.</b> A typo'd qualifier would otherwise
 *       silently widen the search, so it throws {@link IllegalArgumentException}
 *       naming the key (mapped to 400 upstream).</li>
 *   <li><b>Negation</b> is a leading {@code -} on the VALUE of a negatable qualifier:
 *       {@code label=-bug} renders {@code -label:bug}. A value that legitimately
 *       starts with a dash is escaped as {@code \-}. FLAG and BOOLEAN qualifiers
 *       negate via {@code false} instead ({@code isPublic=false} → {@code -is:public}).</li>
 *   <li><b>Comma-joining is OR.</b> {@link ValueKind#MULTI_TEXT} and repeatable
 *       {@link ValueKind#ENUM} qualifiers are joined inside ONE qualifier
 *       ({@code label:"bug","docs"}); repeating the qualifier would AND them. The
 *       single documented exception is {@code no:}, which GitHub only understands
 *       as separate occurrences — see {@link #AND_REPEATED_QUALIFIERS}.</li>
 *   <li><b>Whitespace forces quoting</b> ({@code milestone:"v2 release"}).</li>
 *   <li><b>Dates always carry an explicit {@code Z} timestamp</b>; bare {@code YYYY-MM-DD}
 *       timezone handling is undocumented, and a bare day is widened to that whole
 *       UTC day.</li>
 *   <li><b>Labels search rejects every qualifier</b> — GitHub's label endpoint takes
 *       keywords only.</li>
 * </ul>
 */
@Component
public class SearchQueryBuilder {

    /** GitHub only pages into the first 1000 results of any search; beyond that it 422s. */
    public static final int MAX_REACHABLE_RESULTS = 1000;
    public static final int MAX_PER_PAGE = 100;
    public static final int DEFAULT_PER_PAGE = 30;

    /**
     * Qualifiers that repeat rather than comma-join. {@code no:label no:assignee} is
     * the only form GitHub documents for "missing field" filters; {@code no:label,assignee}
     * is not, so it is not sent.
     */
    private static final Set<String> AND_REPEATED_QUALIFIERS = Set.of("no");

    private static final String NUM = "\\d+";
    private static final Pattern NUMBER_RANGE = Pattern.compile(
            "^(?:" + NUM + "|>=?" + NUM + "|<=?" + NUM + "|" + NUM + "\\.\\." + NUM
                    + "|" + NUM + "\\.\\.\\*|\\*\\.\\." + NUM + ")$");

    private static final String DATE = "\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}:\\d{2}Z)?";
    private static final Pattern DATE_RANGE = Pattern.compile(
            "^(?:(?<op>>=|<=|>|<)(?<single>" + DATE + ")"
                    + "|(?<exact>" + DATE + ")"
                    + "|(?<from>" + DATE + ")\\.\\.(?<to>" + DATE + ")"
                    + "|(?<openFrom>" + DATE + ")\\.\\.\\*"
                    + "|\\*\\.\\.(?<openTo>" + DATE + "))$");

    private static final Pattern BARE_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /**
     * @param spec   the search type being queried
     * @param params raw request parameters, already stripped of the reserved
     *               {@code sort}/{@code order}/{@code page}/{@code perPage} keys
     *               (and, for labels, of the repo-resolution keys) by the controller
     * @return the {@code q} string to send to GitHub; may be empty when the caller
     * supplied nothing (GitHub itself then decides whether that is acceptable)
     * @throws IllegalArgumentException on any unknown key or malformed value, with a
     *                                  message naming the offending parameter
     */
    public String build(SearchTypeSpec spec, Map<String, List<String>> params) {
        Map<String, QualifierSpec> byKey = spec.qualifiersByKey();

        for (String key : params.keySet()) {
            if (byKey.containsKey(key)) {
                continue;
            }
            if (spec.type() == SearchType.LABELS) {
                throw new IllegalArgumentException(
                        "GitHub's label search accepts keywords only — the qualifier '" + key
                                + "' is not supported. Pass keywords in 'q' and scope the search with "
                                + "'repositoryId' (or 'owner' + 'repo').");
            }
            throw new IllegalArgumentException(
                    "Unknown search parameter '" + key + "' for type '" + spec.slug() + "'. Valid parameters: "
                            + String.join(", ", byKey.keySet()) + ".");
        }

        List<String> parts = new ArrayList<>();
        // Iterate the catalog, not the request map, so the rendered query is
        // deterministic regardless of parameter order in the URL.
        for (QualifierSpec qualifier : spec.qualifiers()) {
            List<String> values = params.get(qualifier.key());
            if (values == null || values.isEmpty()) {
                continue;
            }
            List<String> cleaned = values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
            if (cleaned.isEmpty()) {
                continue;
            }
            // GitHub has no qualifier for these, and emitting one would be worse than
            // useless: an unknown word like `repoStars:>=1000` is parsed as free text
            // and silently changes which issues match. The search service applies them.
            if (qualifier.postFilter()) {
                continue;
            }
            if (!qualifier.repeatable() && cleaned.size() > 1) {
                throw new IllegalArgumentException(
                        "'" + qualifier.key() + "' may only be supplied once (got " + cleaned.size() + " values).");
            }
            parts.addAll(render(spec, qualifier, cleaned));
        }

        // GitHub rejects an issue search that names neither issues nor pull
        // requests ("Query must include 'is:issue' or 'is:pull-request'", 422).
        // Nothing in the caller's filters implies one, so default to issues
        // rather than letting a bare Issues-tab search fail.
        if (spec.type() == SearchType.ISSUES && parts.stream().noneMatch(SearchQueryBuilder::namesIssueOrPullRequest)) {
            parts.add(0, "is:issue");
        }

        return String.join(" ", parts);
    }

    /** Does this rendered part satisfy GitHub's issue-or-PR requirement? */
    private static boolean namesIssueOrPullRequest(String part) {
        return part.equals("is:issue") || part.equals("is:pr") || part.equals("is:pull-request")
                || part.startsWith("type:issue") || part.startsWith("type:pr");
    }

    private List<String> render(SearchTypeSpec spec, QualifierSpec qualifier, List<String> values) {
        if (qualifier.isFreeText()) {
            // Raw pass-through: the user may be using AND/OR/NOT and parentheses,
            // which are only valid for issues but are GitHub's problem to reject.
            return List.of(values.getFirst());
        }

        return switch (qualifier.kind()) {
            case FLAG -> List.of(renderFlag(qualifier, values.getFirst()));
            case BOOLEAN -> List.of(renderBoolean(qualifier, values.getFirst()));
            case MULTI_TEXT -> renderJoined(qualifier, values);
            case ENUM -> renderEnum(qualifier, values);
            case NUMBER_RANGE -> List.of(renderRange(qualifier, values.getFirst(), false));
            case DATE_RANGE -> List.of(renderRange(qualifier, values.getFirst(), true));
            case TEXT -> {
                Negatable n = Negatable.parse(qualifier, values.getFirst());
                yield List.of(n.prefix() + qualifier.githubQualifier() + ":" + quoteIfNeeded(n.value()));
            }
        };
    }

    private String renderFlag(QualifierSpec qualifier, String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        String rendered = qualifier.githubQualifier() + ":" + qualifier.flagValue();
        if ("true".equals(value)) {
            return rendered;
        }
        if ("false".equals(value)) {
            if (!qualifier.negatable()) {
                throw new IllegalArgumentException("'" + qualifier.key() + "' cannot be negated; pass true or omit it.");
            }
            return "-" + rendered;
        }
        throw new IllegalArgumentException("Invalid '" + qualifier.key() + "': '" + raw
                + "'. This is a flag — pass true to require " + rendered + ", or false to exclude it.");
    }

    private String renderBoolean(QualifierSpec qualifier, String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException(
                    "Invalid '" + qualifier.key() + "': '" + raw + "'. Expected true or false.");
        }
        return qualifier.githubQualifier() + ":" + value;
    }

    private List<String> renderEnum(QualifierSpec qualifier, List<String> values) {
        List<String> canonical = new ArrayList<>();
        List<String> negated = new ArrayList<>();
        for (String raw : values) {
            for (String single : splitOnCommas(raw)) {
                Negatable n = Negatable.parse(qualifier, single);
                String match = qualifier.allowedValues().stream()
                        .filter(allowed -> allowed.equalsIgnoreCase(n.value()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Invalid '" + qualifier.key() + "': '" + single + "'. Expected one of: "
                                        + String.join(", ", qualifier.allowedValues()) + "."));
                (n.negated() ? negated : canonical).add(match);
            }
        }

        if (AND_REPEATED_QUALIFIERS.contains(qualifier.githubQualifier())) {
            List<String> parts = new ArrayList<>();
            canonical.forEach(v -> parts.add(qualifier.githubQualifier() + ":" + quoteIfNeeded(v)));
            negated.forEach(v -> parts.add("-" + qualifier.githubQualifier() + ":" + quoteIfNeeded(v)));
            return parts;
        }
        return joinParts(qualifier, canonical, negated);
    }

    private List<String> renderJoined(QualifierSpec qualifier, List<String> values) {
        List<String> positive = new ArrayList<>();
        List<String> negated = new ArrayList<>();
        for (String raw : values) {
            for (String single : splitOnCommas(raw)) {
                Negatable n = Negatable.parse(qualifier, single);
                (n.negated() ? negated : positive).add(n.value());
            }
        }
        return joinParts(qualifier, positive, negated);
    }

    private List<String> joinParts(QualifierSpec qualifier, List<String> positive, List<String> negated) {
        List<String> parts = new ArrayList<>();
        if (!positive.isEmpty()) {
            parts.add(qualifier.githubQualifier() + ":" + joinQuoted(positive));
        }
        if (!negated.isEmpty()) {
            parts.add("-" + qualifier.githubQualifier() + ":" + joinQuoted(negated));
        }
        return parts;
    }

    private String joinQuoted(List<String> values) {
        return String.join(",", values.stream().map(SearchQueryBuilder::quoteIfNeeded).toList());
    }

    private String renderRange(QualifierSpec qualifier, String raw, boolean dates) {
        Negatable n = Negatable.parse(qualifier, raw);
        String value = dates ? normalizeDateRange(qualifier, n.value()) : validateNumberRange(qualifier, n.value());
        return n.prefix() + qualifier.githubQualifier() + ":" + value;
    }

    private String validateNumberRange(QualifierSpec qualifier, String value) {
        if (!NUMBER_RANGE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid '" + qualifier.key() + "': '" + value
                    + "'. Expected a number or a range: 5, >5, >=5, <5, <=5, 1..10, 1..*, *..10.");
        }
        return value;
    }

    /**
     * Widens bare {@code YYYY-MM-DD} inputs to explicit UTC instants so the
     * boundaries are unambiguous: a lower bound becomes {@code T00:00:00Z}, an
     * upper bound {@code T23:59:59Z}, and a bare day on its own becomes the whole
     * day as a range. Values that already carry a time are passed through.
     */
    private String normalizeDateRange(QualifierSpec qualifier, String value) {
        Matcher m = DATE_RANGE.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid '" + qualifier.key() + "': '" + value
                    + "'. Expected an ISO date (YYYY-MM-DD, optionally THH:MM:SSZ) or a range: "
                    + "2026-01-01, >=2026-01-01, <2026-01-01, 2026-01-01..2026-02-01, 2026-01-01..*, *..2026-01-01.");
        }

        if (m.group("single") != null) {
            String op = m.group("op");
            // '>' and '<=' sit on the upper edge of the day, '>=' and '<' on the lower.
            boolean upperEdge = ">".equals(op) || "<=".equals(op);
            return op + (upperEdge ? endOfDay(m.group("single")) : startOfDay(m.group("single")));
        }
        if (m.group("exact") != null) {
            String exact = m.group("exact");
            if (!BARE_DATE.matcher(exact).matches()) {
                return exact;
            }
            return startOfDay(exact) + ".." + endOfDay(exact);
        }
        if (m.group("from") != null) {
            return startOfDay(m.group("from")) + ".." + endOfDay(m.group("to"));
        }
        if (m.group("openFrom") != null) {
            return startOfDay(m.group("openFrom")) + "..*";
        }
        return "*.." + endOfDay(m.group("openTo"));
    }

    private static String startOfDay(String date) {
        return BARE_DATE.matcher(date).matches() ? date + "T00:00:00Z" : date;
    }

    private static String endOfDay(String date) {
        return BARE_DATE.matcher(date).matches() ? date + "T23:59:59Z" : date;
    }

    private static List<String> splitOnCommas(String raw) {
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Quoted only when needed, so {@code label:bug} stays readable but {@code label:"good first issue"} works. */
    static String quoteIfNeeded(String value) {
        String stripped = value.replace("\"", "");
        return stripped.chars().anyMatch(Character::isWhitespace) ? "\"" + stripped + "\"" : stripped;
    }

    /**
     * Rejects paging past GitHub's universal 1000-result window before the call
     * goes out — GitHub answers such a request with a 422 that says nothing useful.
     */
    public static void validatePaging(int page, int perPage) {
        if (page < 1) {
            throw new IllegalArgumentException("'page' must be at least 1.");
        }
        if (perPage < 1 || perPage > MAX_PER_PAGE) {
            throw new IllegalArgumentException("'perPage' must be between 1 and " + MAX_PER_PAGE + ".");
        }
        if ((long) page * perPage > MAX_REACHABLE_RESULTS) {
            throw new IllegalArgumentException(
                    "GitHub search only returns the first " + MAX_REACHABLE_RESULTS + " results, so page * perPage "
                            + "must not exceed " + MAX_REACHABLE_RESULTS + " (requested page=" + page
                            + ", perPage=" + perPage + "). Narrow the query instead of paging further.");
        }
    }

    /** A request value with its optional leading {@code -} negation stripped. */
    private record Negatable(boolean negated, String value) {

        static Negatable parse(QualifierSpec qualifier, String raw) {
            if (raw.startsWith("\\-")) {
                // Escape hatch for a value that genuinely begins with a dash.
                return new Negatable(false, raw.substring(1));
            }
            if (raw.startsWith("-") && qualifier.negatable()) {
                String value = raw.substring(1).trim();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("'" + qualifier.key() + "' was negated with no value.");
                }
                return new Negatable(true, value);
            }
            return new Negatable(false, raw);
        }

        String prefix() {
            return negated ? "-" : "";
        }
    }
}
