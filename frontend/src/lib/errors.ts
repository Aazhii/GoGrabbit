export interface FriendlyError {
  title: string;
  detail: string;
  /** Raw message from the API wrapper, shown as small print. */
  raw?: string;
}

/**
 * The shared `request<T>` wrapper throws `Error("(<status>) <message>")`, so the
 * status is recoverable from the message prefix.
 */
function statusOf(message: string): number | null {
  const match = /^\((\d{3})\)\s?/.exec(message);
  return match ? Number(match[1]) : null;
}

function stripStatus(message: string): string {
  return message.replace(/^\(\d{3}\)\s?/, "").trim();
}

export function toFriendlyError(err: unknown): FriendlyError {
  const message = err instanceof Error ? err.message : String(err);
  const status = statusOf(message);
  const raw = stripStatus(message) || undefined;

  switch (status) {
    case 429:
      return {
        title: "GitHub search rate limit reached",
        detail:
          "GitHub caps issue search at 10 requests per minute without a token. Wait about a minute and try again — setting GITHUB_TOKEN on the backend raises the limit to 30 per minute.",
        raw,
      };
    case 502:
    case 503:
    case 504:
      return {
        title: "GitHub is not responding",
        detail:
          "The backend reached GitHub but got an error back. This is usually temporary — try again in a moment. If it keeps happening, check that the owner/repo you filtered on still exists (GitHub does not resolve renamed repos).",
        raw,
      };
    case 400:
    case 422:
      return {
        title: "That filter combination isn't valid",
        detail:
          "GitHub rejected the query. Check the repository is in owner/repo form, that the date range starts before it ends, and that any custom label is spelled the way GitHub has it.",
        raw,
      };
    case 401:
    case 403:
      return {
        title: "GitHub refused the request",
        detail:
          "The backend's GitHub credentials were rejected or the request was forbidden. Check GITHUB_TOKEN on the backend if one is configured.",
        raw,
      };
    default:
      break;
  }

  if (status != null) {
    return { title: `Search failed (${status})`, detail: raw ?? "The backend returned an error.", raw: undefined };
  }

  return {
    title: "Couldn't reach the backend",
    detail:
      "The request never completed. Check that the API is running and that VITE_API_BASE_URL points at it, then try again.",
    raw,
  };
}

export function isAbort(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError";
}
