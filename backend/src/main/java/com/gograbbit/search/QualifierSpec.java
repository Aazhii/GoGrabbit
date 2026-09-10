package com.gograbbit.search;

import java.util.List;

/**
 * One searchable qualifier, described declaratively so both the backend query
 * builder and the frontend form can be driven from the same catalog instead of
 * ~120 hand-written request parameters.
 *
 * @param key              the URL query-parameter name accepted by {@code GET /search/{type}}
 * @param githubQualifier  the GitHub qualifier it renders to ({@code stars}, {@code is}, ...);
 *                         {@code null} means "free text", i.e. the value is appended to
 *                         {@code q} bare with no {@code qualifier:} prefix
 * @param kind             how the value is validated and rendered
 * @param flagValue        only for {@link ValueKind#FLAG}: the fixed value, e.g. {@code public}
 *                         in {@code is:public}
 * @param label            short human label for the UI
 * @param help             one-line explanation for the UI
 * @param group            key of the {@link GroupSpec} this belongs to; always present in the
 *                         owning {@code SearchTypeSpec.groups}
 * @param allowedValues    non-null for {@link ValueKind#ENUM}
 * @param unit             display unit, e.g. {@code KB}; null when unitless
 * @param placeholder      example input for the UI
 * @param negatable        whether a leading {@code -} on the request value negates the qualifier
 *                         (see {@code SearchQueryBuilder} for the exact convention)
 * @param repeatable       whether the parameter may be supplied more than once
 * @param postFilter       true when GitHub CANNOT evaluate this qualifier and the result set
 *                         has to be filtered after fetching. {@code SearchQueryBuilder} leaves
 *                         these out of {@code q} entirely; the search service applies them.
 *                         The UI flags them, because they behave differently: they can only
 *                         narrow what was already scanned.
 */
public record QualifierSpec(
        String key,
        String githubQualifier,
        ValueKind kind,
        String flagValue,
        String label,
        String help,
        String group,
        List<String> allowedValues,
        String unit,
        String placeholder,
        boolean negatable,
        boolean repeatable,
        boolean postFilter
) {

    public QualifierSpec {
        allowedValues = allowedValues == null ? null : List.copyOf(allowedValues);
    }

    /** Free text ({@code q}) — no {@code qualifier:} prefix on the way out. */
    public static QualifierSpec freeText(String group, String help) {
        return new QualifierSpec("q", null, ValueKind.TEXT, null, "Search terms", help, group,
                null, null, "keywords", false, false, false);
    }

    public static QualifierSpec text(String key, String githubQualifier, String label, String help,
                                     String group, String placeholder) {
        return new QualifierSpec(key, githubQualifier, ValueKind.TEXT, null, label, help, group,
                null, null, placeholder, true, false, false);
    }

    public static QualifierSpec enumOf(String key, String githubQualifier, String label, String help,
                                       String group, List<String> allowedValues) {
        return new QualifierSpec(key, githubQualifier, ValueKind.ENUM, null, label, help, group,
                allowedValues, null, null, true, false, false);
    }

    public static QualifierSpec enumMulti(String key, String githubQualifier, String label, String help,
                                          String group, List<String> allowedValues) {
        return new QualifierSpec(key, githubQualifier, ValueKind.ENUM, null, label, help, group,
                allowedValues, null, null, true, true, false);
    }

    public static QualifierSpec numberRange(String key, String githubQualifier, String label, String help,
                                            String group, String unit, String placeholder) {
        return new QualifierSpec(key, githubQualifier, ValueKind.NUMBER_RANGE, null, label, help, group,
                null, unit, placeholder, true, false, false);
    }

    public static QualifierSpec dateRange(String key, String githubQualifier, String label, String help,
                                          String group) {
        return new QualifierSpec(key, githubQualifier, ValueKind.DATE_RANGE, null, label, help, group,
                null, null, ">=2026-01-01", true, false, false);
    }

    public static QualifierSpec bool(String key, String githubQualifier, String label, String help,
                                     String group) {
        return new QualifierSpec(key, githubQualifier, ValueKind.BOOLEAN, null, label, help, group,
                null, null, "true", true, false, false);
    }

    /**
     * @param key       request-parameter name; {@code is}-prefixed by convention so that, e.g.,
     *                  the {@code is:merged} flag and the {@code merged:} date range on ISSUES
     *                  do not collide
     * @param qualifier {@code is} or {@code has}
     */
    public static QualifierSpec flag(String key, String qualifier, String flagValue, String label,
                                     String help, String group) {
        return new QualifierSpec(key, qualifier, ValueKind.FLAG, flagValue, label, help, group,
                null, null, "true", true, false, false);
    }

    public static QualifierSpec multiText(String key, String githubQualifier, String label, String help,
                                          String group, String placeholder) {
        return new QualifierSpec(key, githubQualifier, ValueKind.MULTI_TEXT, null, label, help, group,
                null, null, placeholder, true, true, false);
    }

    /**
     * A numeric range GitHub has no qualifier for, so it is applied to the fetched
     * results instead. {@code githubQualifier} is deliberately null: nothing is ever
     * rendered into {@code q} for these.
     */
    public static QualifierSpec postFilterRange(String key, String label, String help,
                                                String group, String placeholder) {
        return new QualifierSpec(key, null, ValueKind.NUMBER_RANGE, null, label, help, group,
                null, null, placeholder, false, false, true);
    }

    /**
     * Free text is identified by the {@code q} key, not by a null
     * {@code githubQualifier}: a {@link #postFilterRange} has no GitHub-side name
     * either, and conflating the two made a post-filter render into {@code q} as a
     * bare word — the precise failure this feature exists to avoid.
     */
    public boolean isFreeText() {
        return "q".equals(key);
    }
}
