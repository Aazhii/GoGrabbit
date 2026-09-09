package com.gograbbit.search;

/**
 * What a post-filtered search actually looked at, reported so the client can explain
 * a short or empty result set instead of appearing broken.
 *
 * <p>This matters more than it sounds. Freshly created issues overwhelmingly live in
 * small repositories — a sample of the 20 newest {@code good first issue}s contained
 * none at all from a repository with 1000+ stars. So a demanding star range genuinely
 * matches very few of the issues scanned, and without these numbers "3 results" looks
 * like a bug rather than the honest answer.
 *
 * @param scannedIssues how many issues were fetched and examined
 * @param scannedPages  how many upstream pages that took
 * @param matched       how many passed the post-filter
 * @param exhausted     true when the whole reachable result window was covered, so no
 *                      further scanning could find more — as opposed to stopping early
 *                      because the page budget ran out
 * @param pageBudget    the scan ceiling this request was allowed
 */
public record ScanStats(
        int scannedIssues,
        int scannedPages,
        int matched,
        boolean exhausted,
        int pageBudget
) {
}
