package com.gograbbit.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Structural invariants of the catalog. These are the mistakes that would only
 * show up as a GitHub 422 or a broken frontend form, so they are asserted here.
 */
class SearchCatalogTest {

    private final SearchCatalog catalog = new SearchCatalog();

    @Test
    void catalogCoversAllSevenSearchTypesInDeclarationOrder() {
        assertThat(catalog.all()).hasSize(SearchType.values().length);
        assertThat(catalog.all().stream().map(SearchTypeSpec::type).toList())
                .containsExactly(SearchType.values());
    }

    @ParameterizedTest
    @EnumSource(SearchType.class)
    void everyTypeHasAtLeastOneSortExceptTopics(SearchType type) {
        SearchTypeSpec spec = catalog.spec(type);
        if (type == SearchType.TOPICS) {
            // Topics is the one endpoint GitHub gives no sort enum at all.
            assertThat(spec.sorts()).isEmpty();
            assertThat(spec.supportsOrder()).isFalse();
        } else {
            assertThat(spec.sorts()).isNotEmpty();
            assertThat(spec.supportsOrder()).isTrue();
            // best-match is the synthetic "omit sort and order" option and must lead.
            assertThat(spec.sorts().getFirst().value()).isEqualTo(SortSpec.BEST_MATCH);
        }
    }

    @ParameterizedTest
    @EnumSource(SearchType.class)
    void noDuplicateQualifierKeysWithinAType(SearchType type) {
        SearchTypeSpec spec = catalog.spec(type);
        Set<String> seen = new HashSet<>();
        List<String> duplicates = spec.qualifiers().stream()
                .map(QualifierSpec::key)
                .filter(key -> !seen.add(key))
                .toList();
        assertThat(duplicates).as("duplicate qualifier keys in %s", type.slug()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(SearchType.class)
    void noDuplicateSortValuesWithinAType(SearchType type) {
        List<String> values = catalog.spec(type).sorts().stream().map(SortSpec::value).toList();
        assertThat(values).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @EnumSource(SearchType.class)
    void everyQualifierGroupExistsInThatTypesGroups(SearchType type) {
        SearchTypeSpec spec = catalog.spec(type);
        Set<String> groupKeys = spec.groups().stream().map(GroupSpec::key).collect(java.util.stream.Collectors.toSet());
        for (QualifierSpec qualifier : spec.qualifiers()) {
            assertThat(groupKeys)
                    .as("qualifier '%s' of %s references unknown group '%s'",
                            qualifier.key(), type.slug(), qualifier.group())
                    .contains(qualifier.group());
        }
    }

    @ParameterizedTest
    @EnumSource(SearchType.class)
    void everyTypeExposesFreeTextAsTheFirstQualifier(SearchType type) {
        QualifierSpec first = catalog.spec(type).qualifiers().getFirst();
        assertThat(first.key()).isEqualTo("q");
        assertThat(first.isFreeText()).isTrue();
        assertThat(first.githubQualifier()).isNull();
    }

    @ParameterizedTest
    @EnumSource(SearchType.class)
    void kindSpecificFieldsAreConsistent(SearchType type) {
        for (QualifierSpec q : catalog.spec(type).qualifiers()) {
            switch (q.kind()) {
                case ENUM -> assertThat(q.allowedValues())
                        .as("%s.%s is an ENUM and needs allowedValues", type.slug(), q.key())
                        .isNotEmpty();
                case FLAG -> assertThat(q.flagValue())
                        .as("%s.%s is a FLAG and needs a flagValue", type.slug(), q.key())
                        .isNotNull();
                default -> assertThat(q.flagValue())
                        .as("%s.%s is not a FLAG and must not carry a flagValue", type.slug(), q.key())
                        .isNull();
            }
            if (!q.isFreeText()) {
                assertThat(q.githubQualifier()).as("%s.%s", type.slug(), q.key()).isNotBlank();
            }
        }
    }

    @Test
    void endpointSpecificGitHubConstraintsAreRecorded() {
        // Code search is the only authenticated-only endpoint and the only one in
        // its own 10/min bucket, and it needs at least one search term.
        SearchTypeSpec code = catalog.spec(SearchType.CODE);
        assertThat(code.requiresAuth()).isTrue();
        assertThat(code.requiresSearchTerm()).isTrue();
        assertThat(code.rateLimitBucket()).isEqualTo(SearchCatalog.BUCKET_CODE_SEARCH);
        assertThat(code.sorts().stream().map(SortSpec::value)).containsExactly(SortSpec.BEST_MATCH, "indexed");

        // Labels is the only endpoint needing a numeric repository id, and takes
        // keywords only — hence exactly one (free-text) qualifier.
        SearchTypeSpec labels = catalog.spec(SearchType.LABELS);
        assertThat(labels.requiresRepositoryId()).isTrue();
        assertThat(labels.qualifiers()).hasSize(1);

        // Boolean AND/OR/NOT is issues-only.
        for (SearchType type : SearchType.values()) {
            assertThat(catalog.spec(type).supportsBooleanOperators())
                    .as("%s", type.slug())
                    .isEqualTo(type == SearchType.ISSUES);
        }

        // Every other type shares the 30/min search bucket and needs no token.
        for (SearchType type : SearchType.values()) {
            if (type != SearchType.CODE) {
                assertThat(catalog.spec(type).rateLimitBucket()).isEqualTo(SearchCatalog.BUCKET_SEARCH);
                assertThat(catalog.spec(type).requiresAuth()).isFalse();
            }
        }
    }

    @Test
    void issuesSortsIncludeTheReactionsVariantsThatNeedEncoding() {
        List<String> sorts = catalog.spec(SearchType.ISSUES).sorts().stream().map(SortSpec::value).toList();
        assertThat(sorts).contains("reactions-+1", "reactions--1", "reactions-thinking_face", "interactions");
    }

    @Test
    void slugAndPathAgreeWithTheEnum() {
        for (SearchType type : SearchType.values()) {
            SearchTypeSpec spec = catalog.spec(type);
            assertThat(spec.slug()).isEqualTo(type.slug());
            assertThat(spec.path()).isEqualTo("/search/" + type.slug());
        }
    }

    @Test
    void repositoriesDistinguishTopicCountFromTopicName() {
        SearchTypeSpec repos = catalog.spec(SearchType.REPOSITORIES);
        assertThat(repos.qualifier("topics").kind()).isEqualTo(ValueKind.NUMBER_RANGE);
        assertThat(repos.qualifier("topics").label()).isEqualTo("Topic count");
        assertThat(repos.qualifier("topic").kind()).isEqualTo(ValueKind.TEXT);
        assertThat(repos.qualifier("topic").label()).isEqualTo("Topic name");
    }

    @Test
    void unknownSlugIsRejectedWithTheValidOnesListed() {
        assertThatThrownBy(() -> SearchType.fromSlug("repos"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repos")
                .hasMessageContaining("repositories, issues, users, code, commits, topics, labels");
    }

    @Test
    void slugLookupIsCaseInsensitive() {
        assertThat(SearchType.fromSlug("Repositories")).isEqualTo(SearchType.REPOSITORIES);
    }
}
