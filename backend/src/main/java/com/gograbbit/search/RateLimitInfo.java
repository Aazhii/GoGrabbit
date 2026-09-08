package com.gograbbit.search;

import org.springframework.http.HttpHeaders;

import java.time.Instant;

/**
 * GitHub's rate-limit headers, echoed to the client on purpose: the search
 * buckets are small (30/min authenticated, 10/min for code search) and the UI
 * shows the remaining budget rather than surprising the user with a 429.
 *
 * @param resource which bucket the call was billed to, e.g. {@code search} or {@code code_search}
 * @param resetAt  when the bucket refills
 */
public record RateLimitInfo(Integer limit, Integer remaining, Integer used, String resource, Instant resetAt) {

    /** @return null when GitHub sent no rate-limit headers at all (some error paths). */
    public static RateLimitInfo from(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        Integer limit = intHeader(headers, "x-ratelimit-limit");
        Integer remaining = intHeader(headers, "x-ratelimit-remaining");
        Integer used = intHeader(headers, "x-ratelimit-used");
        String resource = headers.getFirst("x-ratelimit-resource");
        Integer reset = intHeader(headers, "x-ratelimit-reset");

        if (limit == null && remaining == null && used == null && resource == null && reset == null) {
            return null;
        }
        return new RateLimitInfo(limit, remaining, used, resource,
                reset == null ? null : Instant.ofEpochSecond(reset));
    }

    private static Integer intHeader(HttpHeaders headers, String name) {
        String raw = headers.getFirst(name);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
