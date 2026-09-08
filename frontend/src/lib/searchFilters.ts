import type {
  SearchQualifier,
  SearchRequestParams,
  SearchTypeDescriptor,
} from "../types";

/**
 * One editable value per qualifier. A single flat shape (rather than a union
 * per `kind`) keeps the reducer in `useGitHubSearch` trivial: every control
 * patches the fields it owns and `serializeQualifier` reads only the fields
 * that its `kind` cares about.
 */
export interface QualifierValue {
  /** Emit the value with a leading "-" (GitHub's negation), when negatable. */
  negated: boolean;
  /** TEXT, single-value ENUM, and the raw-syntax escape hatch for ranges. */
  text: string;
  /** MULTI_TEXT chips and repeatable (multi-select) ENUMs. */
  list: string[];
  /** BOOLEAN tri-state: "" means unset, which is NOT the same as "false". */
  tri: "" | "true" | "false";
  /** FLAG checkbox. */
  flag: boolean;
  comparator: Comparator;
  from: string;
  to: string;
}

export type Comparator = "eq" | "gte" | "lte" | "gt" | "lt" | "between" | "raw";

export const COMPARATORS: { value: Comparator; label: string; hint: string }[] = [
  { value: "eq", label: "is exactly", hint: "5" },
  { value: "gte", label: "at least", hint: ">=5" },
  { value: "lte", label: "at most", hint: "<=5" },
  { value: "gt", label: "more than", hint: ">5" },
  { value: "lt", label: "less than", hint: "<5" },
  { value: "between", label: "between", hint: "1..10" },
  { value: "raw", label: "raw syntax", hint: "*..10" },
];

export const EMPTY_VALUE: QualifierValue = {
  negated: false,
  text: "",
  list: [],
  tri: "",
  flag: false,
  comparator: "gte",
  from: "",
  to: "",
};

export type FilterState = Record<string, QualifierValue>;

export function valueOf(state: FilterState, key: string): QualifierValue {
  return state[key] ?? EMPTY_VALUE;
}

function isListKind(qualifier: SearchQualifier): boolean {
  return qualifier.kind === "MULTI_TEXT" || (qualifier.kind === "ENUM" && qualifier.repeatable);
}

/** GitHub negates a qualifier per value, so a comma list negates element-wise. */
function negate(serialized: string, listLike: boolean): string {
  if (!listLike) return `-${serialized}`;
  return serialized
    .split(",")
    .map((part) => `-${part}`)
    .join(",");
}

function serializeRange(value: QualifierValue): string | null {
  switch (value.comparator) {
    case "raw":
      return value.text.trim() || null;
    case "between": {
      const from = value.from.trim();
      const to = value.to.trim();
      if (!from && !to) return null;
      // "*" is GitHub's open-ended bound: "*..10", "5..*".
      return `${from || "*"}..${to || "*"}`;
    }
    default: {
      const raw = value.from.trim();
      if (!raw) return null;
      const prefix = { eq: "", gte: ">=", lte: "<=", gt: ">", lt: "<" }[value.comparator] ?? "";
      return `${prefix}${raw}`;
    }
  }
}

/**
 * Turns one edited qualifier into the exact string the backend expects as the
 * query-parameter value, or null when the filter is not set.
 */
export function serializeQualifier(
  qualifier: SearchQualifier,
  value: QualifierValue,
): string | null {
  let base: string | null = null;

  switch (qualifier.kind) {
    case "TEXT":
      base = value.text.trim() || null;
      break;
    case "ENUM":
      base = qualifier.repeatable
        ? value.list.length > 0
          ? value.list.join(",")
          : null
        : value.text.trim() || null;
      break;
    case "MULTI_TEXT":
      base = value.list.length > 0 ? value.list.join(",") : null;
      break;
    case "BOOLEAN":
      base = value.tri === "" ? null : value.tri;
      break;
    case "FLAG":
      base = value.flag ? (qualifier.flagValue ?? "true") : null;
      break;
    case "NUMBER_RANGE":
    case "DATE_RANGE":
      base = serializeRange(value);
      break;
    default:
      base = null;
  }

  if (base == null) return null;
  if (value.negated && qualifier.negatable) return negate(base, isListKind(qualifier));
  return base;
}

export function toQualifierParams(
  type: SearchTypeDescriptor,
  filters: FilterState,
): Record<string, string> {
  const params: Record<string, string> = {};
  for (const qualifier of type.qualifiers) {
    const serialized = serializeQualifier(qualifier, valueOf(filters, qualifier.key));
    if (serialized) params[qualifier.key] = serialized;
  }
  return params;
}

export interface ActiveFilter {
  key: string;
  label: string;
  /** What the user sees on the chip, e.g. `stars: >=500`. */
  display: string;
  githubQualifier: string;
}

export function activeFilters(
  type: SearchTypeDescriptor,
  filters: FilterState,
): ActiveFilter[] {
  const active: ActiveFilter[] = [];
  for (const qualifier of type.qualifiers) {
    const serialized = serializeQualifier(qualifier, valueOf(filters, qualifier.key));
    if (!serialized) continue;
    active.push({
      key: qualifier.key,
      label: qualifier.label,
      display: `${qualifier.label}: ${serialized}`,
      githubQualifier: qualifier.githubQualifier,
    });
  }
  return active;
}

/* ---------------- whole-form state ---------------- */

export interface SearchFormState {
  q: string;
  /** Only used by types with `requiresRepositoryId` (labels). */
  owner: string;
  repo: string;
  sort: string;
  order: "asc" | "desc";
  perPage: number;
  filters: FilterState;
}

export function initialFormState(type: SearchTypeDescriptor | null): SearchFormState {
  return {
    q: "",
    owner: "",
    repo: "",
    sort: type?.sorts[0]?.value ?? "",
    order: "desc",
    perPage: 30,
    filters: {},
  };
}

export function toRequestParams(
  type: SearchTypeDescriptor,
  form: SearchFormState,
  page: number,
): SearchRequestParams {
  return {
    q: form.q.trim() || undefined,
    owner: type.requiresRepositoryId ? form.owner.trim() || undefined : undefined,
    repo: type.requiresRepositoryId ? form.repo.trim() || undefined : undefined,
    // Topics has no sorts at all; sending a sort there would be a lie.
    sort: type.sorts.length > 0 && form.sort ? form.sort : undefined,
    order: type.supportsOrder && type.sorts.length > 0 && form.sort ? form.order : undefined,
    page,
    perPage: form.perPage,
    qualifiers: toQualifierParams(type, form.filters),
  };
}

/** Stable string used to tell "edited but not searched yet" from "applied". */
export function formFingerprint(type: SearchTypeDescriptor, form: SearchFormState): string {
  const params = toRequestParams(type, form, 1);
  return JSON.stringify({
    q: params.q ?? "",
    owner: params.owner ?? "",
    repo: params.repo ?? "",
    sort: params.sort ?? "",
    order: params.order ?? "",
    perPage: params.perPage ?? 0,
    qualifiers: Object.entries(params.qualifiers ?? {}).sort(([a], [b]) => a.localeCompare(b)),
  });
}

/** Per-qualifier dirty check, so an unapplied control can be marked in place. */
export function qualifierDirty(
  qualifier: SearchQualifier,
  current: FilterState,
  applied: FilterState | null,
): boolean {
  if (!applied) return serializeQualifier(qualifier, valueOf(current, qualifier.key)) != null;
  return (
    serializeQualifier(qualifier, valueOf(current, qualifier.key)) !==
    serializeQualifier(qualifier, valueOf(applied, qualifier.key))
  );
}

/**
 * The same search, on github.com — so the query preview doubles as a way to
 * check our interpretation against the real thing.
 */
export function githubSearchUrl(slug: string, query: string): string {
  const params = new URLSearchParams({ q: query, type: slug });
  return `https://github.com/search?${params.toString()}`;
}

/** "1,234 repositories" / "1 repository" — GitHub prints a bare number. */
export function formatCount(count: number, typeLabel: string): string {
  const noun = count === 1 ? singular(typeLabel) : typeLabel;
  return `${count.toLocaleString()} ${noun.toLowerCase()}`;
}

function singular(label: string): string {
  if (label.endsWith("ies")) return `${label.slice(0, -3)}y`;
  if (label.endsWith("s")) return label.slice(0, -1);
  return label;
}

/** Splits pasted "owner/repo" or a github.com URL into its two halves. */
export function splitOwnerRepo(input: string): { owner: string; repo: string } {
  const trimmed = input
    .trim()
    .replace(/^https?:\/\/github\.com\//i, "")
    .replace(/\/+$/, "");
  if (!trimmed) return { owner: "", repo: "" };
  const [owner = "", repo = ""] = trimmed.split("/");
  return { owner, repo };
}
