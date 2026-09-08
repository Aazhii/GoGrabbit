import EmptyState from "./EmptyState";
import ErrorNotice from "./ErrorNotice";
import IssueCard from "./IssueCard";
import Pagination from "./Pagination";
import Spinner from "./Spinner";
import type { FriendlyError } from "../lib/errors";
import type { IssueSearchResponse } from "../types";

/** GitHub refuses to page past this many results, whatever totalCount says. */
const PAGING_CEILING = 1000;

interface SearchResultsProps {
  loading: boolean;
  pristine: boolean;
  error: FriendlyError | null;
  data: IssueSearchResponse | null;
  onRetry: () => void;
  onPageChange: (page: number) => void;
}

export default function SearchResults({
  loading,
  pristine,
  error,
  data,
  onRetry,
  onPageChange,
}: SearchResultsProps) {
  return (
    <section className="results" aria-busy={loading} aria-live="polite">
      <h2>Results</h2>

      {error && <ErrorNotice error={error} onRetry={onRetry} />}

      {loading && <Spinner label="Searching GitHub…" />}

      {!loading && !error && pristine && (
        <EmptyState title="No search run yet">
          <p>
            Pick your labels and hit <strong>Search issues</strong>. The defaults look for open
            &ldquo;good first issue&rdquo; tickets across all of GitHub, newest first.
          </p>
        </EmptyState>
      )}

      {!loading && !error && !pristine && data && data.items.length === 0 && (
        <EmptyState title="No issues matched those filters">
          <p>Try loosening things:</p>
          <ul>
            <li>Widen the &ldquo;Created&rdquo; window (or set it to Any time).</li>
            <li>Drop the repository filter, or check the owner/repo spelling.</li>
            <li>Untick a label — issues need <em>every</em> selected label to match.</li>
            <li>Clear the keywords box.</li>
          </ul>
        </EmptyState>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <>
          <p className="results-summary">
            {data.resultsCapped
              ? `${data.totalCount.toLocaleString()} matches — GitHub only lets you page through the first ${PAGING_CEILING.toLocaleString()}.`
              : `${data.totalCount.toLocaleString()} ${data.totalCount === 1 ? "match" : "matches"}.`}
          </p>

          {data.incompleteResults && (
            <p className="warning-note">
              GitHub&rsquo;s search timed out, so these results are partial. Narrowing the filters
              usually gives a complete answer.
            </p>
          )}

          <ul className="issue-cards">
            {data.items.map((issue) => (
              <IssueCard key={issue.id} issue={issue} />
            ))}
          </ul>

          <Pagination
            page={data.page}
            perPage={data.perPage}
            itemCount={data.items.length}
            hasNextPage={data.hasNextPage}
            loading={loading}
            onPageChange={onPageChange}
          />

          {data.query && (
            <details className="query-details">
              <summary>GitHub query used</summary>
              <code>{data.query}</code>
            </details>
          )}
        </>
      )}
    </section>
  );
}
