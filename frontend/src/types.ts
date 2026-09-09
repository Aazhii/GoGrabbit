export interface WatchedRepo {
  id: string;
  owner: string;
  repo: string;
  labels: string[];
  intervalMinutes: number;
  active: boolean;
  createdAt: string;
}

export interface SeenIssue {
  id: string;
  owner: string;
  repo: string;
  title: string;
  url: string;
  postedAt: string;
  labeledAt: string;
  notifiedAt: string;
}

export interface IssueLabel {
  name: string;
  /** GitHub hex colour without a leading "#", e.g. "7057ff". May be empty. */
  color: string;
}

export interface IssueUser {
  login: string;
  avatarUrl: string;
  url: string;
}

export type IssueState = "open" | "closed";

export interface IssueSearchResult {
  id: number;
  number: number;
  title: string;
  url: string;
  state: IssueState;
  owner: string;
  repo: string;
  repositoryFullName: string;
  labels: IssueLabel[];
  createdAt: string;
  updatedAt: string;
  comments: number;
  /** GitHub can return a null author for issues from deleted accounts. */
  author: IssueUser | null;
  assignees: IssueUser[];
}

export interface IssueSearchResponse {
  items: IssueSearchResult[];
  totalCount: number;
  page: number;
  perPage: number;
  hasNextPage: boolean;
  /** True when totalCount exceeds GitHub's hard 1000-result paging ceiling. */
  resultsCapped: boolean;
  /** True when GitHub's search timed out and returned partial results. */
  incompleteResults: boolean;
  query: string;
}

export type IssueStateFilter = "open" | "closed" | "all";
export type IssueSortField = "created" | "updated" | "comments" | "bestmatch";
export type IssueSortOrder = "asc" | "desc";

export interface IssueSearchFilters {
  q?: string;
  labels?: string[];
  state?: IssueStateFilter;
  owner?: string;
  repo?: string;
  createdWithinDays?: number;
  createdFrom?: string;
  createdTo?: string;
  sort?: IssueSortField;
  order?: IssueSortOrder;
  page?: number;
  perPage?: number;
}

/* ------------------------------------------------------------------ *
 * Multi-type GitHub search (GET /search/catalog, GET /search/{slug})
 *
 * `SearchItem` / `SearchTypeSlug` live in ./types/searchItems.ts — the
 * per-type item shapes are owned by the result-card layer.
 * ------------------------------------------------------------------ */

import type { SearchItem, SearchTypeSlug } from "./types/searchItems";

export type { SearchItem, SearchTypeSlug };

export type QualifierKind =
  | "TEXT"
  | "ENUM"
  | "NUMBER_RANGE"
  | "DATE_RANGE"
  | "BOOLEAN"
  | "FLAG"
  | "MULTI_TEXT";

export interface SearchQualifier {
  key: string;
  /** The GitHub qualifier this maps to, e.g. "stars" in `stars:>=500`. */
  githubQualifier: string;
  kind: QualifierKind;
  /** Fixed value emitted by a FLAG qualifier; null for every other kind. */
  flagValue: string | null;
  label: string;
  help: string | null;
  /** Key of the group this belongs to; groups are rendered in catalog order. */
  group: string;
  allowedValues: string[] | null;
  unit: string | null;
  placeholder: string | null;
  negatable: boolean;
  repeatable: boolean;
  /**
   * True when the backend applies this qualifier AFTER fetching from GitHub
   * rather than handing it to GitHub as part of the query — `repoStars` is the
   * case that exists today, because REST `/search/issues` cannot filter on a
   * repository's star count at all (`stars:>1000` there is parsed as free
   * text). Such a filter makes a search scan several pages and can legitimately
   * return few or no rows; the response's `scan` block reports what happened.
   */
  postFilter?: boolean;
}

export interface SearchQualifierGroup {
  key: string;
  label: string;
}

export interface SearchSortOption {
  value: string;
  label: string;
}

export interface SearchTypeDescriptor {
  type: string;
  slug: SearchTypeSlug;
  label: string;
  requiresAuth: boolean;
  rateLimitBucket: string;
  requiresRepositoryId: boolean;
  requiresSearchTerm: boolean;
  supportsBooleanOperators: boolean;
  supportsOrder: boolean;
  sorts: SearchSortOption[];
  groups: SearchQualifierGroup[];
  qualifiers: SearchQualifier[];
}

export interface SearchCatalog {
  types: SearchTypeDescriptor[];
}

export interface SearchRateLimit {
  limit: number;
  remaining: number;
  used: number;
  resource: string;
  resetAt: string;
}

/**
 * What a post-filtered search actually did.
 *
 * A `repoStars` filter cannot be pushed down into GitHub's query, so the
 * backend fetches pages of issues and keeps the ones whose repository clears
 * the threshold. Because freshly created issues overwhelmingly live in small
 * repositories, examining hundreds of issues and matching a handful — or none
 * — is a correct outcome, not a failure. These counters are what let the UI
 * say so instead of looking broken.
 */
export interface ScanStats {
  /** How many issues were fetched and examined. */
  scannedIssues: number;
  /** How many GraphQL pages that took. */
  scannedPages: number;
  /** How many passed the star filter. */
  matched: number;
  /** True when the end of GitHub's reachable window was reached — no more exist. */
  exhausted: boolean;
  /** The maximum number of pages this request was allowed to scan. */
  pageBudget: number;
}

export interface SearchResponse {
  type: string;
  items: SearchItem[];
  totalCount: number;
  page: number;
  perPage: number;
  hasNextPage: boolean;
  resultsCapped: boolean;
  incompleteResults: boolean;
  /** The exact `q` string the backend sent to GitHub. */
  query: string;
  rateLimit: SearchRateLimit | null;
  /** Present ONLY when a star post-filter was applied — see ScanStats. */
  scan?: ScanStats | null;
}

export interface SearchRequestParams {
  q?: string;
  sort?: string;
  order?: "asc" | "desc";
  page?: number;
  perPage?: number;
  /** Only for types with requiresRepositoryId (labels). */
  owner?: string;
  repo?: string;
  /** qualifierKey -> already-serialised GitHub value, e.g. { stars: ">=500" }. */
  qualifiers?: Record<string, string>;
}
