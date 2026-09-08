import type { IssueSearchFilters, IssueStateFilter } from "../types";

export const PRESET_LABELS = ["good first issue", "help wanted", "bug", "documentation"];

export const CREATED_PRESETS = [
  { value: "any", label: "Any time", days: undefined },
  { value: "1", label: "Last 24 hours", days: 1 },
  { value: "7", label: "Last 7 days", days: 7 },
  { value: "30", label: "Last 30 days", days: 30 },
  { value: "custom", label: "Custom range…", days: undefined },
] as const;

export type CreatedPreset = (typeof CREATED_PRESETS)[number]["value"];

export const SORT_OPTIONS = [
  { value: "newest", label: "Newest" },
  { value: "oldest", label: "Oldest" },
  { value: "updated", label: "Recently updated" },
  { value: "comments", label: "Most commented" },
  { value: "bestmatch", label: "Best match" },
] as const;

export type SortChoice = (typeof SORT_OPTIONS)[number]["value"];

export interface SearchFormValues {
  q: string;
  /** Which of PRESET_LABELS are ticked. */
  presetLabels: string[];
  /** Free-text extra labels, comma separated. */
  customLabels: string;
  repository: string;
  state: IssueStateFilter;
  created: CreatedPreset;
  createdFrom: string;
  createdTo: string;
  sort: SortChoice;
  perPage: number;
}

export const DEFAULT_FORM_VALUES: SearchFormValues = {
  q: "",
  presetLabels: ["good first issue"],
  customLabels: "",
  repository: "",
  state: "open",
  created: "any",
  createdFrom: "",
  createdTo: "",
  sort: "newest",
  perPage: 30,
};

const SORT_MAP: Record<SortChoice, Pick<IssueSearchFilters, "sort" | "order">> = {
  newest: { sort: "created", order: "desc" },
  oldest: { sort: "created", order: "asc" },
  updated: { sort: "updated", order: "desc" },
  comments: { sort: "comments", order: "desc" },
  bestmatch: { sort: "bestmatch", order: "desc" },
};

/** Splits a free-text "owner/repo" (or bare "owner") into its parts. */
export function splitRepository(input: string): { owner?: string; repo?: string } {
  const trimmed = input.trim().replace(/^https?:\/\/github\.com\//i, "").replace(/\/+$/, "");
  if (!trimmed) return {};
  const [owner, repo] = trimmed.split("/");
  return { owner: owner || undefined, repo: repo || undefined };
}

export function collectLabels(values: SearchFormValues): string[] {
  const custom = values.customLabels
    .split(",")
    .map((l) => l.trim())
    .filter(Boolean);
  const seen = new Set<string>();
  return [...values.presetLabels, ...custom].filter((l) => {
    const key = l.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function toFilters(values: SearchFormValues, page: number): IssueSearchFilters {
  const { owner, repo } = splitRepository(values.repository);
  const preset = CREATED_PRESETS.find((p) => p.value === values.created);
  const isCustom = values.created === "custom";

  return {
    q: values.q.trim() || undefined,
    labels: collectLabels(values),
    state: values.state,
    owner,
    repo,
    createdWithinDays: isCustom ? undefined : preset?.days,
    createdFrom: isCustom ? values.createdFrom || undefined : undefined,
    createdTo: isCustom ? values.createdTo || undefined : undefined,
    ...SORT_MAP[values.sort],
    page,
    perPage: values.perPage,
  };
}
