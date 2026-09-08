package com.gograbbit.search;

/**
 * How a {@link QualifierSpec}'s incoming request value is validated and rendered
 * into GitHub search syntax.
 */
public enum ValueKind {

    /** Free-form single value. Quoted automatically when it contains whitespace. */
    TEXT,

    /** One value out of {@code QualifierSpec.allowedValues}. */
    ENUM,

    /** {@code 5}, {@code >5}, {@code >=5}, {@code <5}, {@code <=5}, {@code 1..10}, {@code 1..*}, {@code *..10}. */
    NUMBER_RANGE,

    /**
     * Same range operators as {@link #NUMBER_RANGE} but over ISO dates
     * ({@code YYYY-MM-DD}, optionally {@code THH:MM:SSZ}). Always rendered with an
     * explicit {@code Z} timestamp — bare-date timezone handling is undocumented.
     */
    DATE_RANGE,

    /** {@code true}/{@code false}, rendered as {@code archived:true}. */
    BOOLEAN,

    /**
     * A qualifier whose value the catalog fixes, e.g. {@code is:public}. The request
     * value is {@code true} (emit it) or {@code false} (emit it negated).
     */
    FLAG,

    /**
     * Several values comma-joined inside ONE qualifier — that is GitHub's OR, e.g.
     * {@code label:"bug","good first issue"}. Repeating the qualifier would AND them.
     */
    MULTI_TEXT
}
