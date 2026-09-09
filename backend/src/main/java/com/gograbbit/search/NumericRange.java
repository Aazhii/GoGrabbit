package com.gograbbit.search;

/**
 * GitHub's numeric range syntax, parsed into something we can test values against.
 *
 * <p>Needed because {@code repoStars} is a post-filter: GitHub's issue search has no
 * stars qualifier, so the range is never sent upstream and has to be evaluated here
 * instead. The accepted syntax is deliberately identical to what
 * {@link SearchQueryBuilder} would have rendered, so the same input means the same
 * thing whether GitHub applies it or we do.
 *
 * <p>Accepts {@code 5}, {@code >5}, {@code >=5}, {@code <5}, {@code <=5},
 * {@code 1..10}, {@code 1..*} and {@code *..10}.
 *
 * @param min inclusive lower bound, or null for unbounded
 * @param max inclusive upper bound, or null for unbounded
 */
public record NumericRange(Long min, Long max) {

    public boolean matches(long value) {
        return (min == null || value >= min) && (max == null || value <= max);
    }

    /**
     * @throws IllegalArgumentException with a message naming {@code key}, so a bad
     *                                  filter surfaces as a 400 the caller can act on
     */
    public static NumericRange parse(String key, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw invalid(key, raw);
        }

        try {
            if (value.contains("..")) {
                String[] parts = value.split("\\.\\.", -1);
                if (parts.length != 2) {
                    throw invalid(key, raw);
                }
                Long low = bound(key, raw, parts[0]);
                Long high = bound(key, raw, parts[1]);
                if (low == null && high == null) {
                    // "*..*" constrains nothing, which is almost certainly a mistake
                    // rather than an intent to match everything.
                    throw invalid(key, raw);
                }
                if (low != null && high != null && low > high) {
                    throw new IllegalArgumentException("Invalid '" + key + "': '" + raw
                            + "'. The range starts above where it ends.");
                }
                return new NumericRange(low, high);
            }
            if (value.startsWith(">=")) {
                return new NumericRange(nonNegative(key, raw, value.substring(2)), null);
            }
            if (value.startsWith("<=")) {
                return new NumericRange(null, nonNegative(key, raw, value.substring(2)));
            }
            if (value.startsWith(">")) {
                return new NumericRange(nonNegative(key, raw, value.substring(1)) + 1, null);
            }
            if (value.startsWith("<")) {
                return new NumericRange(null, nonNegative(key, raw, value.substring(1)) - 1);
            }
            long exact = nonNegative(key, raw, value);
            return new NumericRange(exact, exact);
        } catch (NumberFormatException ex) {
            throw invalid(key, raw);
        }
    }

    /**
     * {@code *} is GitHub's open bound. An EMPTY side ({@code "1.."}) is not — GitHub
     * requires the star, and accepting the bare form here would mean this filter
     * silently allowed syntax the GitHub-side builder rejects.
     */
    private static Long bound(String key, String raw, String part) {
        String trimmed = part.trim();
        if ("*".equals(trimmed)) {
            return null;
        }
        return nonNegative(key, raw, trimmed);
    }

    /**
     * Counts are never negative, and GitHub's own range grammar is digits-only, so a
     * sign is rejected rather than quietly accepted ({@code >-5} would otherwise
     * become "at least -4", matching everything).
     */
    private static long nonNegative(String key, String raw, String part) {
        String trimmed = part.trim();
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(Character::isDigit)) {
            throw invalid(key, raw);
        }
        return Long.parseLong(trimmed);
    }

    private static IllegalArgumentException invalid(String key, String raw) {
        return new IllegalArgumentException("Invalid '" + key + "': '" + raw
                + "'. Expected a number or a range: 5, >5, >=5, <5, <=5, 1..10, 1..*, *..10.");
    }
}
