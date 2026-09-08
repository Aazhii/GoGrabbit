package com.gograbbit.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests: no Spring context, no database, no network. The catalog and
 * the builder are both plain objects.
 */
class SearchQueryBuilderTest {

    private final SearchCatalog catalog = new SearchCatalog();
    private final SearchQueryBuilder builder = new SearchQueryBuilder();

    private String build(SearchType type, Object... keyValuePairs) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String key = (String) keyValuePairs[i];
            Object value = keyValuePairs[i + 1];
            List<String> values = value instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of(String.valueOf(value));
            params.merge(key, values, (a, b) -> {
                var merged = new java.util.ArrayList<>(a);
                merged.addAll(b);
                return merged;
            });
        }
        return builder.build(catalog.spec(type), params);
    }

    // ------------------------------------------------------------- NUMBER_RANGE

    @ParameterizedTest
    @ValueSource(strings = {"5", ">5", ">=5", "<5", "<=5", "1..10", "1..*", "*..10"})
    void numberRangeAcceptsEveryGitHubRangeForm(String range) {
        assertThat(build(SearchType.REPOSITORIES, "stars", range)).isEqualTo("stars:" + range);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1..", ">", "5.5", "1..2..3", ">=", "*..*"})
    void numberRangeRejectsMalformedInput(String range) {
        assertThatThrownBy(() -> build(SearchType.REPOSITORIES, "stars", range))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stars");
    }

    @Test
    void numberRangeKeepsUnitBearingQualifiersUnchanged() {
        // size is in KB for repositories and in bytes for code — the unit is UI
        // metadata only, the value is passed through verbatim either way.
        assertThat(build(SearchType.REPOSITORIES, "size", ">=1000")).isEqualTo("size:>=1000");
        assertThat(build(SearchType.CODE, "q", "parser", "size", ">1000"))
                .isEqualTo("parser size:>1000");
    }

    // --------------------------------------------------------------- DATE_RANGE

    @Test
    void dateRangeAlwaysEmitsExplicitZTimestamps() {
        assertThat(build(SearchType.ISSUES, "created", ">=2026-01-01"))
                .isEqualTo("is:issue created:>=2026-01-01T00:00:00Z");
        assertThat(build(SearchType.ISSUES, "created", "<2026-01-01"))
                .isEqualTo("is:issue created:<2026-01-01T00:00:00Z");
        // The upper edge of a day for the operators that include it.
        assertThat(build(SearchType.ISSUES, "created", "<=2026-01-01"))
                .isEqualTo("is:issue created:<=2026-01-01T23:59:59Z");
        assertThat(build(SearchType.ISSUES, "created", ">2026-01-01"))
                .isEqualTo("is:issue created:>2026-01-01T23:59:59Z");
    }

    @Test
    void dateRangeAcceptsRangesAndWidensBareDaysToWholeUtcDays() {
        assertThat(build(SearchType.ISSUES, "created", "2026-01-01..2026-02-01"))
                .isEqualTo("is:issue created:2026-01-01T00:00:00Z..2026-02-01T23:59:59Z");
        assertThat(build(SearchType.ISSUES, "created", "2026-01-01..*"))
                .isEqualTo("is:issue created:2026-01-01T00:00:00Z..*");
        assertThat(build(SearchType.ISSUES, "created", "*..2026-01-01"))
                .isEqualTo("is:issue created:*..2026-01-01T23:59:59Z");
        // A bare day on its own becomes that entire UTC day.
        assertThat(build(SearchType.ISSUES, "created", "2026-01-01"))
                .isEqualTo("is:issue created:2026-01-01T00:00:00Z..2026-01-01T23:59:59Z");
    }

    @Test
    void dateRangePassesThroughAnExplicitTimestampUntouched() {
        assertThat(build(SearchType.ISSUES, "created", ">=2026-01-01T09:30:00Z"))
                .isEqualTo("is:issue created:>=2026-01-01T09:30:00Z");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-1-1", "yesterday", ">=2026-01-01T09:30:00", "2026-01-01..", "2026/01/01"})
    void dateRangeRejectsMalformedInput(String value) {
        assertThatThrownBy(() -> build(SearchType.ISSUES, "created", value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("created");
    }

    // -------------------------------------------------------------- TEXT/quoting

    @Test
    void multiWordValuesAreQuoted() {
        assertThat(build(SearchType.ISSUES, "milestone", "v2 release"))
                .isEqualTo("is:issue milestone:\"v2 release\"");
        assertThat(build(SearchType.ISSUES, "milestone", "v2")).isEqualTo("is:issue milestone:v2");
    }

    @Test
    void enumValueContainingWhitespaceIsQuoted() {
        assertThat(build(SearchType.ISSUES, "reason", "not planned"))
                .isEqualTo("is:issue reason:\"not planned\"");
    }

    @Test
    void freeTextIsPassedThroughWithoutAQualifierPrefix() {
        assertThat(build(SearchType.ISSUES, "q", "memory leak NOT flaky"))
                .isEqualTo("is:issue memory leak NOT flaky");
    }

    // -------------------------------------------------------------- MULTI_TEXT

    @Test
    void multiTextCommaJoinsInsideOneQualifierBecauseThatIsOr() {
        // One label: qualifier with comma-separated values is OR. Repeating
        // `label:` would AND them and require every label at once.
        assertThat(build(SearchType.ISSUES, "label", List.of("bug", "good first issue")))
                .isEqualTo("is:issue label:bug,\"good first issue\"");
        assertThat(build(SearchType.ISSUES, "label", "bug,docs"))
                .isEqualTo("is:issue label:bug,docs");
    }

    @Test
    void enumMultiCommaJoinsButNoRepeatsAsSeparateQualifiers() {
        assertThat(build(SearchType.REPOSITORIES, "in", List.of("name", "description")))
                .isEqualTo("in:name,description");
        // `no:` is the documented exception: GitHub only understands separate occurrences.
        assertThat(build(SearchType.ISSUES, "no", List.of("label", "milestone")))
                .isEqualTo("is:issue no:label no:milestone");
    }

    // ------------------------------------------------------------------ FLAG

    @Test
    void flagEmitsTheFixedQualifierValue() {
        assertThat(build(SearchType.REPOSITORIES, "isPublic", "true")).isEqualTo("is:public");
        assertThat(build(SearchType.REPOSITORIES, "hasFundingFile", "true"))
                .isEqualTo("has:funding-file");
        assertThat(build(SearchType.REPOSITORIES, "isPublic", "false")).isEqualTo("-is:public");
    }

    @Test
    void flagRejectsANonBooleanValue() {
        assertThatThrownBy(() -> build(SearchType.REPOSITORIES, "isPublic", "public"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isPublic");
    }

    // --------------------------------------------------------------- BOOLEAN

    @Test
    void booleanEmitsQualifierColonTrueOrFalse() {
        assertThat(build(SearchType.REPOSITORIES, "archived", "true")).isEqualTo("archived:true");
        assertThat(build(SearchType.REPOSITORIES, "archived", "false")).isEqualTo("archived:false");
    }

    @Test
    void booleanRejectsANonBooleanValue() {
        assertThatThrownBy(() -> build(SearchType.REPOSITORIES, "archived", "yes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("archived");
    }

    // -------------------------------------------------------------- negation

    @Test
    void leadingDashNegatesAQualifier() {
        assertThat(build(SearchType.ISSUES, "label", "-bug")).isEqualTo("is:issue -label:bug");
        assertThat(build(SearchType.ISSUES, "author", "-octocat")).isEqualTo("is:issue -author:octocat");
        // Positive and negated values of the same qualifier stay in separate clauses.
        assertThat(build(SearchType.ISSUES, "label", List.of("bug", "-wontfix")))
                .isEqualTo("is:issue label:bug -label:wontfix");
    }

    @Test
    void escapedDashIsALiteralValueNotANegation() {
        assertThat(build(SearchType.ISSUES, "label", "\\-weird")).isEqualTo("is:issue label:-weird");
    }

    // ------------------------------------------------------------ unknown keys

    @Test
    void unknownKeyIsRejectedAndNamed() {
        assertThatThrownBy(() -> build(SearchType.REPOSITORIES, "starz", ">5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("starz")
                .hasMessageContaining("repositories");
    }

    @Test
    void aQualifierValidForAnotherTypeIsStillUnknownHere() {
        // `reactions` is an issues qualifier; on repositories it must not silently pass.
        assertThatThrownBy(() -> build(SearchType.REPOSITORIES, "reactions", ">5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reactions");
    }

    // ---------------------------------------------------------------- ENUM

    @Test
    void enumRejectsAValueOutsideAllowedValues() {
        assertThatThrownBy(() -> build(SearchType.ISSUES, "state", "merged"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state")
                .hasMessageContaining("open, closed");
    }

    @Test
    void enumIsCaseInsensitiveButEmitsTheCanonicalValue() {
        assertThat(build(SearchType.ISSUES, "state", "OPEN")).isEqualTo("is:issue state:open");
    }

    @ParameterizedTest
    @CsvSource({
            "fork,true,fork:true",
            "fork,only,fork:only"
    })
    void enumAcceptsEveryAllowedValue(String key, String value, String expected) {
        assertThat(build(SearchType.REPOSITORIES, key, value)).isEqualTo(expected);
    }

    // --------------------------------------------------------------- LABELS

    @Test
    void labelSearchRejectsEveryQualifierBecauseGitHubTakesKeywordsOnly() {
        assertThatThrownBy(() -> build(SearchType.LABELS, "q", "bug", "repo", "spotify/backstage"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keywords only")
                .hasMessageContaining("repo");
    }

    @Test
    void labelSearchStillAcceptsKeywords() {
        assertThat(build(SearchType.LABELS, "q", "good first issue")).isEqualTo("good first issue");
    }

    // -------------------------------------------------------------- repeatable

    @Test
    void aNonRepeatableQualifierSuppliedTwiceIsRejected() {
        assertThatThrownBy(() -> build(SearchType.REPOSITORIES, "stars", List.of(">5", "<100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stars");
    }

    // ----------------------------------------------------------------- paging

    @Test
    void pagingBeyondGitHubs1000ResultWindowIsRejected() {
        assertThatThrownBy(() -> SearchQueryBuilder.validatePaging(11, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
        assertThatThrownBy(() -> SearchQueryBuilder.validatePaging(0, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> SearchQueryBuilder.validatePaging(1, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("perPage");
    }

    @Test
    void pagingUpToTheLastReachablePageIsAllowed() {
        SearchQueryBuilder.validatePaging(10, 100);
        SearchQueryBuilder.validatePaging(1, 1);
        SearchQueryBuilder.validatePaging(33, 30);
    }

    // ---------------------------------------------------------------- ordering

    @Test
    void renderedQueryFollowsCatalogOrderNotRequestOrder() {
        String a = build(SearchType.REPOSITORIES, "stars", ">=500", "language", "java");
        String b = build(SearchType.REPOSITORIES, "language", "java", "stars", ">=500");
        assertThat(a).isEqualTo(b).isEqualTo("stars:>=500 language:java");
    }

    @Test
    void noParametersProducesAnEmptyQuery() {
        assertThat(builder.build(catalog.spec(SearchType.REPOSITORIES), Map.of())).isEmpty();
    }

    @Test
    void blankValuesAreIgnoredRatherThanRenderedAsEmptyQualifiers() {
        assertThat(build(SearchType.REPOSITORIES, "language", "  ", "stars", ">1"))
                .isEqualTo("stars:>1");
    }

    @Test
    void issueSearchDefaultsToIssuesWhenNeitherIssueNorPullRequestIsNamed() {
        // GitHub 422s an issue query naming neither, so the builder supplies it.
        assertThat(build(SearchType.ISSUES, "state", "open")).contains("is:issue");
    }

    @Test
    void issueSearchDoesNotForceIssuesWhenTypeIsAlreadyGiven() {
        String q = build(SearchType.ISSUES, "type", "pr");
        assertThat(q).contains("type:pr").doesNotContain("is:issue");
    }
}
