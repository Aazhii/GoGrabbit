import { formatAbsolute, formatRelative } from "../lib/relativeTime";
import type { SearchRateLimit } from "../types";

const WARN_AT = 3;

interface RateLimitMeterProps {
  rateLimit: SearchRateLimit | null;
  /** True for code search, which sits in its own much smaller bucket. */
  isCodeSearch: boolean;
}

/**
 * GitHub's search limits are per minute and per bucket, and the UI spends one
 * request per search — so the budget is worth showing before it runs out
 * rather than only explaining it in the 429 error.
 */
export default function RateLimitMeter({ rateLimit, isCodeSearch }: RateLimitMeterProps) {
  if (!rateLimit) return null;

  const { limit, remaining, resource, resetAt } = rateLimit;
  const pct = limit > 0 ? Math.max(0, Math.min(100, (remaining / limit) * 100)) : 0;
  const low = remaining <= WARN_AT;
  const resets = formatRelative(resetAt);

  return (
    <div className={low ? "rate-meter rate-meter-low" : "rate-meter"}>
      <div className="rate-meter-head">
        <span className="rate-meter-label">
          {remaining.toLocaleString()}/{limit.toLocaleString()} requests left
        </span>
        <span className="rate-meter-bucket" title={`GitHub rate-limit bucket: ${resource}`}>
          {resource} bucket
        </span>
      </div>
      <div
        className="rate-meter-bar"
        role="meter"
        aria-valuenow={remaining}
        aria-valuemin={0}
        aria-valuemax={limit}
        aria-label={`GitHub ${resource} search requests remaining`}
      >
        <span style={{ width: `${pct}%` }} />
      </div>
      <p className="rate-meter-note">
        {resets && (
          <span title={formatAbsolute(resetAt)}>
            Resets {resets}
            {isCodeSearch || low ? " · " : ""}
          </span>
        )}
        {isCodeSearch && <span>Code search has its own 10/min bucket.</span>}
        {low && !isCodeSearch && (
          <span role="status">Nearly out — the next few searches may be rejected with a 429.</span>
        )}
      </p>
      {low && isCodeSearch && (
        <p className="rate-meter-note" role="status">
          Nearly out — wait for the reset before searching again.
        </p>
      )}
    </div>
  );
}
