import { EMPTY_VALUE, type FilterState, type QualifierValue } from "./searchFilters";
import type { SearchTypeDescriptor } from "../types";

/**
 * One-click starters. These are the "minimal path": most people want one of a
 * handful of well-known searches, and should never have to assemble it by hand.
 *
 * Every key below is a real catalog key (verified against GET /search/catalog);
 * a preset that names a qualifier the running backend does not expose is
 * dropped rather than silently sending an unknown parameter — the backend
 * rejects unknown keys with a 400.
 */
export interface FilterPreset {
  id: string;
  label: string;
  description: string;
  filters: FilterState;
}

function value(patch: Partial<QualifierValue>): QualifierValue {
  return { ...EMPTY_VALUE, ...patch };
}

/** Named parameters keep each literal checked against FilterState directly —
 *  an inline array of differing object shapes would widen into a union first. */
function preset(
  id: string,
  label: string,
  description: string,
  filters: FilterState,
): FilterPreset {
  return { id, label, description, filters };
}

/** ISO date `n` days before today, in UTC — the form ranges speak YYYY-MM-DD. */
function daysAgo(n: number): string {
  const date = new Date(Date.now() - n * 86_400_000);
  return date.toISOString().slice(0, 10);
}

const BY_SLUG: Record<string, () => FilterPreset[]> = {
  issues: () => [
    preset(
      "unassigned-gfi",
      "Unassigned good first issues",
      'Open issues labelled "good first issue" with nobody assigned yet.',
      {
        label: value({ list: ["good first issue"] }),
        no: value({ list: ["assignee"] }),
        state: value({ text: "open" }),
        type: value({ text: "issue" }),
      },
    ),
    preset("help-wanted", "Help wanted", 'Open issues labelled "help wanted".', {
      label: value({ list: ["help wanted"] }),
      state: value({ text: "open" }),
      type: value({ text: "issue" }),
    }),
    preset("recently-created", "Recently created", "Open issues created in the last 7 days.", {
      created: value({ comparator: "gte", from: daysAgo(7) }),
      state: value({ text: "open" }),
      type: value({ text: "issue" }),
    }),
    /**
     * The whole reason `repoStars` exists: "issues created recently, in
     * projects with lots of stars". GitHub cannot answer that in one query —
     * `stars:` inside an issue search is parsed as free text — so `repoStars`
     * is post-filtered and the search scans several pages to find matches.
     * Expect a small result set, and read the scan note in the results header.
     *
     * `created` uses the relative-window comparator ("in the last N days",
     * `from` holding the day count) rather than a frozen `daysAgo()` date, so
     * the preset stays correct however long the tab has been open.
     */
    preset(
      "fresh-in-popular-repos",
      "Fresh issues in popular repos",
      'Open issues labelled "good first issue" from the last 30 days, in repositories with at least 1,000 stars. Stars are filtered after fetching, so this scans several pages and returns few rows.',
      {
        created: value({ comparator: "within", from: "30" }),
        repoStars: value({ comparator: "gte", from: "1000" }),
        label: value({ list: ["good first issue"] }),
        state: value({ text: "open" }),
        type: value({ text: "issue" }),
      },
    ),
  ],

  repositories: () => [
    preset(
      "has-gfi",
      "Has good first issues",
      "Repositories with at least one open good-first-issue.",
      { goodFirstIssues: value({ comparator: "gte", from: "1" }) },
    ),
    preset(
      "trending-stars",
      "Trending by stars",
      "500+ stars and pushed to within the last 30 days.",
      {
        stars: value({ comparator: "gte", from: "500" }),
        pushed: value({ comparator: "gte", from: daysAgo(30) }),
      },
    ),
  ],
};

export function presetsFor(type: SearchTypeDescriptor): FilterPreset[] {
  const build = BY_SLUG[type.slug];
  if (!build) return [];
  const known = new Set(type.qualifiers.map((q) => q.key));
  return build()
    .map((preset) => ({
      ...preset,
      filters: Object.fromEntries(
        Object.entries(preset.filters).filter(([key]) => known.has(key)),
      ),
    }))
    .filter((preset) => Object.keys(preset.filters).length > 0);
}

/** True when the current rows are exactly this preset — lets it render active. */
export function presetIsActive(preset: FilterPreset, filters: FilterState): boolean {
  const presetKeys = Object.keys(preset.filters).sort();
  const currentKeys = Object.keys(filters).sort();
  if (presetKeys.length !== currentKeys.length) return false;
  if (presetKeys.some((key, i) => key !== currentKeys[i])) return false;
  return presetKeys.every(
    (key) => JSON.stringify(preset.filters[key]) === JSON.stringify(filters[key]),
  );
}
