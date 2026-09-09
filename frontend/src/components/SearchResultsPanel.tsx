import EmptyState from "./EmptyState";
import ErrorNotice from "./ErrorNotice";
import Pagination from "./Pagination";
import QueryPreview from "./QueryPreview";
import RateLimitMeter from "./RateLimitMeter";
import Spinner from "./Spinner";
import { ResultCard } from "./results";
import { formatCount } from "../lib/searchFilters";
import type { FriendlyError } from "../lib/errors";
import type { ScanStats, SearchResponse, SearchTypeDescriptor } from "../types";

/** GitHub refuses to page past this many results, whatever totalCount says. */
const PAGING_CEILING = 1000;

interface SearchResultsPanelProps {
  type: SearchTypeDescriptor;
  loading: boolean;
  pristine: boolean;
  error: FriendlyError | null;
  data: SearchResponse | null;
  sort: string;
  order: "asc" | "desc";
  onSortChange: (sort: string) => void;
  onOrderChange: (order: "asc" | "desc") => void;
  onRetry: () => void;
  onPageChange: (page: number) => void;
}

export default function SearchResultsPanel({
  type,
  loading,
  pristine,
  error,
  data,
  sort,
  order,
  onSortChange,
  onOrderChange,
  onRetry,
  onPageChange,
}: SearchResultsPanelProps) {
  const hasSorts = type.sorts.length > 0;

  return (
    <section className="search-results" aria-busy={loading} aria-live="polite">
      <div className="results-header">
        <h2 className="results-count">
          {data
            ? data.resultsCapped
              ? `${formatCount(data.totalCount, type.label)} — first ${PAGING_CEILING.toLocaleString()} reachable`
              : formatCount(data.totalCount, type.label)
            : `Search ${type.label.toLowerCase()}`}
        </h2>

        {/* Topics has no sorts at all, so the whole control disappears rather
            than offering a choice GitHub would ignore. */}
        {hasSorts && (
          <div className="results-sort">
            <label htmlFor="results-sort-select">Sort</label>
            <select
              id="results-sort-select"
              value={sort}
              onChange={(e) => onSortChange(e.target.value)}
            >
              {type.sorts.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            {type.supportsOrder && (
              <button
                type="button"
                className="order-toggle"
                aria-pressed={order === "asc"}
                title={order === "desc" ? "Descending — click for ascending" : "Ascending — click for descending"}
                onClick={() => onOrderChange(order === "desc" ? "asc" : "desc")}
              >
                {order === "desc" ? "↓ Desc" : "↑ Asc"}
              </button>
            )}
          </div>
        )}
      </div>

      {data?.rateLimit && (
        <RateLimitMeter rateLimit={data.rateLimit} isCodeSearch={type.slug === "code"} />
      )}

      {data?.query && <QueryPreview query={data.query} slug={type.slug} />}

      {data?.incompleteResults && (
        <p className="warning-note">
          GitHub&rsquo;s search timed out, so these results are partial. Narrowing the filters
          usually gives a complete answer.
        </p>
      )}

      {data?.resultsCapped && (
        <p className="warning-note">
          GitHub only lets you page through the first {PAGING_CEILING.toLocaleString()} results of
          any search. Add filters to bring the total under that.
        </p>
      )}

      {data?.scan && <ScanNote scan={data.scan} />}

      {error && <ErrorNotice error={error} onRetry={onRetry} />}

      {loading && <Spinner label={`Searching GitHub ${type.label.toLowerCase()}…`} />}

      {!loading && !error && pristine && (
        <EmptyState title="No search run yet">
          <p>
            Type a keyword above, or set a filter on the left, and hit{" "}
            <strong>Search</strong>. The query GitHub receives is shown here afterwards, so you can
            see exactly which qualifiers your choices produced.
          </p>
        </EmptyState>
      )}

      {/* A post-filtered search that matched nothing is not an empty search —
          the scan note above already says what was examined and what to
          loosen, and stacking "No issues matched" on top of it would read as a
          failure. So the generic empty state stands down whenever `scan` is
          present. */}
      {!loading && !error && !pristine && data && data.items.length === 0 && !data.scan && (
        <EmptyState title={`No ${type.label.toLowerCase()} matched`}>
          <p>Try loosening things:</p>
          <ul>
            <li>Remove a filter chip above — every qualifier narrows the result set.</li>
            <li>Widen a range (a comparator like &ldquo;at least&rdquo; beats an exact value).</li>
            <li>Turn off any &ldquo;not&rdquo; toggle you set — those exclude matches.</li>
            <li>Check spelling in the keyword box, or clear it entirely.</li>
            {type.requiresRepositoryId && (
              <li>Confirm the owner/repo exists — GitHub does not resolve renamed repos.</li>
            )}
          </ul>
        </EmptyState>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <>
          <ul className="result-list">
            {data.items.map((item, index) => (
              <li key={resultKey(item, index)}>
                <ResultCard type={type.slug} item={item} />
              </li>
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
        </>
      )}
    </section>
  );
}

/**
 * What a star-filtered search actually did.
 *
 * A repository's star count cannot be filtered inside a GitHub issue query at
 * all, so the backend scans pages of issues and keeps the ones whose repo
 * clears the threshold. Newly created issues overwhelmingly live in small
 * repositories, so "hundreds examined, a handful — or none — kept" is the
 * normal, correct outcome. Every branch below therefore explains the numbers
 * and names the next move; none of them is phrased as an error, and this is
 * deliberately the amber informational note rather than the red error style.
 */
function ScanNote({ scan }: { scan: ScanStats }) {
  const budget = count(scan.pageBudget, "page", "pages");

  // Nothing was fetched at all: the star filter never even got a say, so
  // pointing at the star minimum would send the user the wrong way.
  if (scan.scannedIssues <= 0) {
    return (
      <p className="warning-note scan-note">
        <strong>Nothing to scan</strong> — GitHub returned no issues at all for this query, so the
        star filter never came into play.
        <span className="scan-note-hint">
          Loosen the other filters first: stars are applied afterwards, to whatever GitHub returns.
        </span>
      </p>
    );
  }

  return (
    <p className="warning-note scan-note">
      <strong>
        {scan.scannedIssues === 1
          ? "Scanned the single issue GitHub returned for this query"
          : `Scanned the first ${scan.scannedIssues.toLocaleString()} issues GitHub returned for this query`}{" "}
        ({count(scan.scannedPages, "page", "pages")})
      </strong>
      {scan.matched === 0
        ? " — none of them are in repositories that meet your star filter."
        : scan.matched === 1
          ? " — 1 is in a repository meeting your star filter."
          : ` — ${scan.matched.toLocaleString()} are in repositories meeting your star filter.`}
      <span className="scan-note-hint">
        {scan.matched === 0
          ? "That is a real result, not an error: stars are checked after fetching, and newly created issues mostly live in small repositories. "
          : ""}
        {scan.exhausted
          ? `There are no more issues to scan — that was the full window GitHub will return for this query, so a scan budget bigger than ${budget} would not help. Lower the star minimum or widen the date range to match more.`
          : `More issues exist beyond this request’s scan budget of ${budget}. Narrowing the date range or lowering the star minimum will surface more of them.`}
      </span>
    </p>
  );
}

/** "1 page" / "5 pages" — thousands-separated, so a 500-issue scan reads right. */
function count(n: number, one: string, many: string): string {
  return `${n.toLocaleString()} ${n === 1 ? one : many}`;
}

/**
 * Item shapes are owned by the result-card layer, so the list only reaches for
 * an id/url if one happens to be there and falls back to the index otherwise.
 */
function resultKey(item: unknown, index: number): string {
  if (item && typeof item === "object") {
    const record = item as Record<string, unknown>;
    for (const field of ["id", "url", "htmlUrl", "fullName", "sha", "login", "name"]) {
      const candidate = record[field];
      if (typeof candidate === "string" || typeof candidate === "number") {
        return `${field}:${candidate}`;
      }
    }
  }
  return `index:${index}`;
}
