package com.gograbbit.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The star post-filter is only trustworthy if this agrees with what GitHub would
 * have done for a qualifier it can evaluate itself, so the accepted syntax and the
 * inclusive/exclusive boundaries are pinned here.
 */
class NumericRangeTest {

    @Test
    void exactValueMatchesOnlyItself() {
        NumericRange range = NumericRange.parse("repoStars", "5");
        assertThat(range.matches(5)).isTrue();
        assertThat(range.matches(4)).isFalse();
        assertThat(range.matches(6)).isFalse();
    }

    @Test
    void atLeastIsInclusive() {
        NumericRange range = NumericRange.parse("repoStars", ">=1000");
        assertThat(range.matches(1000)).isTrue();
        assertThat(range.matches(999)).isFalse();
        assertThat(range.matches(Long.MAX_VALUE)).isTrue();
    }

    @Test
    void greaterThanIsExclusive() {
        NumericRange range = NumericRange.parse("repoStars", ">1000");
        assertThat(range.matches(1000)).isFalse();
        assertThat(range.matches(1001)).isTrue();
    }

    @Test
    void atMostIsInclusive() {
        NumericRange range = NumericRange.parse("repoStars", "<=100");
        assertThat(range.matches(100)).isTrue();
        assertThat(range.matches(101)).isFalse();
        assertThat(range.matches(0)).isTrue();
    }

    @Test
    void lessThanIsExclusive() {
        NumericRange range = NumericRange.parse("repoStars", "<100");
        assertThat(range.matches(100)).isFalse();
        assertThat(range.matches(99)).isTrue();
    }

    @Test
    void closedRangeIncludesBothEnds() {
        NumericRange range = NumericRange.parse("repoStars", "100..200");
        assertThat(range.matches(100)).isTrue();
        assertThat(range.matches(200)).isTrue();
        assertThat(range.matches(99)).isFalse();
        assertThat(range.matches(201)).isFalse();
    }

    @Test
    void openUpperBound() {
        NumericRange range = NumericRange.parse("repoStars", "500..*");
        assertThat(range.matches(500)).isTrue();
        assertThat(range.matches(1_000_000)).isTrue();
        assertThat(range.matches(499)).isFalse();
    }

    @Test
    void openLowerBound() {
        NumericRange range = NumericRange.parse("repoStars", "*..50");
        assertThat(range.matches(0)).isTrue();
        assertThat(range.matches(50)).isTrue();
        assertThat(range.matches(51)).isFalse();
    }

    @Test
    void surroundingWhitespaceIsTolerated() {
        assertThat(NumericRange.parse("repoStars", "  >= 1000 ").matches(1000)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", ">", ">=", "1..", "..10", "1..2..3",
            "*..*", ">=abc", "1.5", "-5", ">-5"})
    void rejectsMalformedInput(String raw) {
        assertThatThrownBy(() -> NumericRange.parse("repoStars", raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repoStars");
    }

    @Test
    void rejectsABackwardsRangeWithADistinctMessage() {
        assertThatThrownBy(() -> NumericRange.parse("repoStars", "500..100"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("starts above where it ends");
    }

    @Test
    void nullIsRejectedRatherThanTreatedAsUnbounded() {
        assertThatThrownBy(() -> NumericRange.parse("repoStars", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
