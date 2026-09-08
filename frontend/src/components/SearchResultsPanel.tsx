import EmptyState from "./EmptyState";
import ErrorNotice from "./ErrorNotice";
import Pagination from "./Pagination";
import QueryPreview from "./QueryPreview";
import RateLimitMeter from "./RateLimitMeter";
import Spinner from "./Spinner";
import { ResultCard } from "./results";
import { formatCount } from "../lib/searchFilters";
import type { FriendlyError } from "../lib/errors";
import type { SearchResponse, SearchTypeDescriptor } from "../types";

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

      {!loading && !error && !pristine && data && data.items.length === 0 && (
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
