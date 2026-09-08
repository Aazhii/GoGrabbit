import { useEffect, useState } from "react";
import { githubSearchUrl } from "../lib/searchFilters";

interface QueryPreviewProps {
  query: string;
  slug: string;
}

/**
 * Shows the exact `q` the backend built. GitHub makes you memorise qualifier
 * syntax; seeing the generated query next to the controls that produced it
 * teaches it, and the github.com link lets you verify it against the source.
 */
export default function QueryPreview({ query, slug }: QueryPreviewProps) {
  // Storing *which* query was copied (rather than a bare boolean) means a new
  // query automatically stops showing a stale "Copied" — no reset effect.
  const [copiedQuery, setCopiedQuery] = useState<string | null>(null);
  const copied = copiedQuery !== null && copiedQuery === query;

  useEffect(() => {
    if (!copied) return;
    const timer = window.setTimeout(() => setCopiedQuery(null), 1600);
    return () => window.clearTimeout(timer);
  }, [copied]);

  if (!query) return null;

  async function copy() {
    try {
      await navigator.clipboard.writeText(query);
      setCopiedQuery(query);
    } catch {
      // Clipboard is unavailable (insecure origin, or permission denied) — the
      // query is already selectable on screen, so fail quietly rather than
      // blocking the page with an alert.
      setCopiedQuery(null);
    }
  }

  return (
    <div className="query-preview">
      <span className="query-preview-label">GitHub query</span>
      <code className="query-preview-code">{query}</code>
      <button type="button" className="link-button" onClick={copy}>
        {copied ? "Copied" : "Copy"}
      </button>
      <a
        className="query-preview-link"
        href={githubSearchUrl(slug, query)}
        target="_blank"
        rel="noreferrer"
      >
        Open on github.com ↗
      </a>
    </div>
  );
}
