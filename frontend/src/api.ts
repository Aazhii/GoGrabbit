import type {
  IssueSearchFilters,
  IssueSearchResponse,
  SearchCatalog,
  SearchRequestParams,
  SearchResponse,
  SeenIssue,
  WatchedRepo,
} from "./types";
import type { SearchTypeSlug } from "./types/searchItems";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // `...init` is spread last so callers keep full control, but `headers` is
  // merged explicitly first — otherwise an init that carries its own headers
  // would silently drop the JSON content type.
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });

  if (!response.ok) {
    const body = await response.text();
    let message = body;
    try {
      message = JSON.parse(body).message ?? body;
    } catch {
      // not JSON — fall back to raw body
    }
    throw new Error(`(${response.status}) ${message}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function listRepos(): Promise<WatchedRepo[]> {
  return request("/repos");
}

export function createRepo(input: {
  owner: string;
  repo: string;
  labels: string[];
  intervalMinutes: number;
}): Promise<WatchedRepo> {
  return request("/repos", { method: "POST", body: JSON.stringify(input) });
}

export function deleteRepo(id: string): Promise<void> {
  return request(`/repos/${id}`, { method: "DELETE" });
}

export function pollRepo(id: string): Promise<{ newIssuesFound: number }> {
  return request(`/repos/${id}/poll`, { method: "POST" });
}

export interface IssueFilters {
  sinceDays?: number;
  owner?: string;
  repo?: string;
}

export function listRecentIssues(filters: IssueFilters = {}): Promise<SeenIssue[]> {
  const params = new URLSearchParams();
  if (filters.sinceDays != null) params.set("sinceDays", String(filters.sinceDays));
  if (filters.owner) params.set("owner", filters.owner);
  if (filters.repo) params.set("repo", filters.repo);

  const query = params.toString();
  return request(`/issues/recent${query ? `?${query}` : ""}`);
}

export function searchIssues(
  filters: IssueSearchFilters = {},
  signal?: AbortSignal,
): Promise<IssueSearchResponse> {
  const params = new URLSearchParams();
  if (filters.q) params.set("q", filters.q);
  if (filters.labels && filters.labels.length > 0) params.set("labels", filters.labels.join(","));
  if (filters.state) params.set("state", filters.state);
  if (filters.owner) params.set("owner", filters.owner);
  if (filters.repo) params.set("repo", filters.repo);
  if (filters.createdWithinDays != null) {
    params.set("createdWithinDays", String(filters.createdWithinDays));
  }
  if (filters.createdFrom) params.set("createdFrom", filters.createdFrom);
  if (filters.createdTo) params.set("createdTo", filters.createdTo);
  if (filters.sort) params.set("sort", filters.sort);
  if (filters.order) params.set("order", filters.order);
  if (filters.page != null) params.set("page", String(filters.page));
  if (filters.perPage != null) params.set("perPage", String(filters.perPage));

  const query = params.toString();
  return request(`/issues/search${query ? `?${query}` : ""}`, { signal });
}

/* ---------------- multi-type GitHub search ---------------- */

/**
 * The catalog drives the entire search UI — which type tabs exist, which
 * filters each type offers and how each one is rendered. Fetched once.
 */
export function getSearchCatalog(signal?: AbortSignal): Promise<SearchCatalog> {
  return request("/search/catalog", { signal });
}

export function runSearch(
  slug: SearchTypeSlug,
  params: SearchRequestParams = {},
  signal?: AbortSignal,
): Promise<SearchResponse> {
  const search = new URLSearchParams();
  if (params.q) search.set("q", params.q);
  if (params.owner) search.set("owner", params.owner);
  if (params.repo) search.set("repo", params.repo);
  if (params.sort) search.set("sort", params.sort);
  if (params.order) search.set("order", params.order);
  if (params.page != null) search.set("page", String(params.page));
  if (params.perPage != null) search.set("perPage", String(params.perPage));

  // Qualifier values arrive pre-serialised (">=500", "1..10", "-java") so the
  // transport layer never has to know about GitHub's range syntax.
  for (const [key, value] of Object.entries(params.qualifiers ?? {})) {
    if (value) search.set(key, value);
  }

  const query = search.toString();
  return request(`/search/${slug}${query ? `?${query}` : ""}`, { signal });
}
