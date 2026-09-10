/**
 * Item shapes for the multi-type GitHub search UI.
 *
 * Field names mirror the backend's JSON exactly — do not rename them here.
 * GitHub omits a great deal (ghost authors, unlicensed repos, topic logos),
 * so anything optional is typed as nullable *and* optional: the cards must
 * render without it.
 */

export type SearchTypeSlug =
  | "repositories"
  | "issues"
  | "users"
  | "code"
  | "commits"
  | "topics"
  | "labels";

/** A GitHub account as it appears embedded in another item. */
export interface ActorRef {
  login: string;
  avatarUrl?: string | null;
  url?: string | null;
}

export interface RepositoryItem {
  id: number;
  fullName: string;
  name: string;
  owner?: ActorRef | null;
  description?: string | null;
  url: string;
  stars?: number | null;
  forks?: number | null;
  watchers?: number | null;
  openIssues?: number | null;
  language?: string | null;
  topics?: string[] | null;
  license?: string | null;
  sizeKb?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  pushedAt?: string | null;
  archived?: boolean | null;
  isTemplate?: boolean | null;
  isFork?: boolean | null;
  visibility?: string | null;
  homepage?: string | null;
  defaultBranch?: string | null;
}

export interface IssueLabelRef {
  name: string;
  color?: string | null;
}

export interface IssueItem {
  id: number;
  number: number;
  title: string;
  url: string;
  state: string;
  stateReason?: string | null;
  owner?: string | null;
  repo?: string | null;
  repositoryFullName?: string | null;
  /**
   * The issue's repository stargazer count.
   *
   * REST `/search/issues` cannot return (or filter on) repository stars —
   * `stars:>1000` inside an issue query is silently parsed as FREE TEXT — so
   * this is only populated when the search went via GraphQL. `null` therefore
   * means "not known", never "zero": the card must render nothing at all
   * rather than print a 0 it cannot stand behind.
   */
  stars?: number | null;
  /** The repository's primary language. Same GraphQL-only caveat as `stars`. */
  language?: string | null;
  labels?: IssueLabelRef[] | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  closedAt?: string | null;
  comments?: number | null;
  reactions?: number | null;
  author?: ActorRef | null;
  assignees?: ActorRef[] | null;
  isPullRequest?: boolean | null;
  draft?: boolean | null;
  milestone?: string | null;
}

export interface UserItem {
  login: string;
  id: number;
  type?: string | null;
  name?: string | null;
  avatarUrl?: string | null;
  url: string;
  bio?: string | null;
  location?: string | null;
  company?: string | null;
  blog?: string | null;
  email?: string | null;
  publicRepos?: number | null;
  publicGists?: number | null;
  followers?: number | null;
  following?: number | null;
  createdAt?: string | null;
  hireable?: boolean | null;
}

export interface CodeRepositoryRef {
  fullName: string;
  url?: string | null;
  owner?: string | null;
  avatarUrl?: string | null;
}

export interface CodeItem {
  name: string;
  path: string;
  sha?: string | null;
  url: string;
  repository?: CodeRepositoryRef | null;
  language?: string | null;
  fileSize?: number | null;
}

export interface CommitRepositoryRef {
  fullName: string;
  url?: string | null;
}

export interface CommitItem {
  sha: string;
  shortSha?: string | null;
  message?: string | null;
  url: string;
  author?: ActorRef | null;
  authorName?: string | null;
  authorEmail?: string | null;
  authoredAt?: string | null;
  committerName?: string | null;
  committedAt?: string | null;
  repository?: CommitRepositoryRef | null;
  commentCount?: number | null;
  isMerge?: boolean | null;
}

export interface TopicItem {
  name: string;
  url?: string | null;
  displayName?: string | null;
  shortDescription?: string | null;
  description?: string | null;
  createdBy?: string | null;
  released?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  featured?: boolean | null;
  curated?: boolean | null;
  repositoryCount?: number | null;
  logoUrl?: string | null;
}

export interface LabelItem {
  id: number;
  name: string;
  color?: string | null;
  description?: string | null;
  isDefault?: boolean | null;
  url: string;
}

export type SearchItem =
  | RepositoryItem
  | IssueItem
  | UserItem
  | CodeItem
  | CommitItem
  | TopicItem
  | LabelItem;
