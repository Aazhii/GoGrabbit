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
