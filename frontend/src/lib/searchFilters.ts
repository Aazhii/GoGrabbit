import type {
  SearchQualifier,
  SearchRequestParams,
  SearchTypeDescriptor,
} from "../types";

/**
 * One editable value per qualifier. A single flat shape (rather than a union
 * per `kind`) keeps the reducer in `GitHubSearchTab` trivial: every control
 * patches the fields it owns and `emitQualifier` reads only the fields that its
 * `kind` cares about.
 *
 * Presence in `FilterState` means "this filter row exists"; a row whose value
 * is still blank simply emits nothing. That is what lets the sidebar be a
 * builder — you add the filters you want instead of being shown all 42.
 */
export interface QualifierValue {
  /** Emit the value with a leading "-" (GitHub's negation), when negatable. */
  negated: boolean;
  /** TEXT, single-value ENUM, and the raw-syntax escape hatch for ranges. */
  text: string;
  /** MULTI_TEXT chips and repeatable (multi-select) ENUMs. */
  list: string[];
  /**
   * How the values in `list` combine. GitHub comma-joins inside one qualifier
   * as OR (`label:"a","b"`) and ANDs a repeated qualifier
   * (`label:"a" label:"b"`), so this single field covers both.
   * Ignored by every non-list kind. Defaults to "or" — today's behaviour.
   */
  join: JoinMode;
  comparator: Comparator;
  from: string;
  to: string;
}

export type JoinMode = "or" | "and";

export type Comparator = "within" | "eq" | "gte" | "lte" | "gt" | "lt" | "between" | "raw";

/**
 * Relative windows for date filters — "opened in the last 7 days" is the
 * question people actually ask, and typing an absolute date to ask it is a
 * chore. Serialized as >=YYYY-MM-DD computed at search time, which the backend
 * widens to an explicit UTC timestamp.
 */
export const RELATIVE_WINDOWS: { value: string; label: string; days: number }[] = [
  { value: "1", label: "24 hours", days: 1 },
  { value: "3", label: "3 days", days: 3 },
  { value: "7", label: "week", days: 7 },
  { value: "14", label: "2 weeks", days: 14 },
  { value: "30", label: "month", days: 30 },
  { value: "90", label: "3 months", days: 90 },
  { value: "180", label: "6 months", days: 180 },
  { value: "365", label: "year", days: 365 },
];

/**
 * "in the last" is only meaningful for dates — a count like good-first-issues
 * has no relative window, and offering one produced `good-first-issues:>=2026-09-01`,
 * which GitHub rejects.
 */
export function comparatorsFor(kind: string) {
  return kind === "DATE_RANGE"
    ? COMPARATORS
    : COMPARATORS.filter((c) => c.value !== "within");
}

/** Date filters open on a relative window; everything else on "at least". */
export function defaultComparator(kind: string): Comparator {
  return kind === "DATE_RANGE" ? "within" : "gte";
}

export const COMPARATORS: { value: Comparator; label: string; hint: string }[] = [
  { value: "within", label: "in the last", hint: "7 days" },
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
  join: "or",
  comparator: "gte",
  from: "",
  to: "",
};

export type FilterState = Record<string, QualifierValue>;

export function valueOf(state: FilterState, key: string): QualifierValue {
  return state[key] ?? EMPTY_VALUE;
}

/**
 * The free-text qualifier (`q`) has no GitHub-side name and is already the
 * page's search box — it must never appear as a filter row you can add twice.
 */
export function isFreeTextQualifier(qualifier: SearchQualifier): boolean {
  return qualifier.key === "q" || qualifier.githubQualifier == null;
}

/** Qualifiers the builder offers as rows: everything except the search box. */
export function selectableQualifiers(type: SearchTypeDescriptor): SearchQualifier[] {
  return type.qualifiers.filter((q) => !isFreeTextQualifier(q));
}

/** Kinds that hold a list of values, and therefore can be OR-ed or AND-ed. */
export function isListKind(qualifier: SearchQualifier): boolean {
  return qualifier.kind === "MULTI_TEXT" || (qualifier.kind === "ENUM" && qualifier.repeatable);
}

/**
 * Mirrors `SearchQueryBuilder.AND_REPEATED_QUALIFIERS` on the backend: GitHub
 * only understands `no:` as separate occurrences, so its comma list already
 * means AND and offering an OR/AND toggle for it would be a lie.
 */
const ALWAYS_AND = new Set(["no"]);

export function joinIsFixed(qualifier: SearchQualifier): boolean {
  return ALWAYS_AND.has(qualifier.githubQualifier);
}

/** The effective join for a qualifier, honouring the always-AND exceptions. */
export function effectiveJoin(qualifier: SearchQualifier, value: QualifierValue): JoinMode {
  if (joinIsFixed(qualifier)) return "and";
  return value.join === "and" ? "and" : "or";
}

export function supportsJoinToggle(qualifier: SearchQualifier): boolean {
  return isListKind(qualifier) && !joinIsFixed(qualifier);
}

/** Kinds whose "is / is not" operator is meaningful. Ranges use a comparator. */
export function supportsNegation(qualifier: SearchQualifier): boolean {
  return qualifier.negatable && qualifier.kind !== "NUMBER_RANGE" && qualifier.kind !== "DATE_RANGE";
}

/** Same rule as the backend's `quoteIfNeeded`, for the AND fragments we emit. */
function quoteIfNeeded(value: string): string {
  const stripped = value.replace(/"/g, "");
  return /\s/.test(stripped) ? `"${stripped}"` : stripped;
}

function cleanList(value: QualifierValue): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of value.list) {
    const item = raw.trim();
    if (item && !seen.has(item)) {
      seen.add(item);
      out.push(item);
    }
  }
  return out;
}

function serializeRange(value: QualifierValue, kind: string): string | null {
  switch (value.comparator) {
    case "within": {
      if (kind !== "DATE_RANGE") return null;
      const days = Number(value.from.trim() || value.text.trim());
      if (!Number.isFinite(days) || days <= 0) return null;
      const since = new Date(Date.now() - days * 86_400_000);
      return `>=${since.toISOString().slice(0, 10)}`;
    }
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
 * What one edited qualifier contributes to the request.
 *
 * `param` is the value for the qualifier's own request parameter, which the
 * backend renders (comma-joining list values inside ONE qualifier — GitHub's
 * OR). `qParts` are raw GitHub fragments appended to the free-text `q`, which
 * the backend passes through verbatim; that is the only channel that can emit
 * the SAME qualifier more than once, which is GitHub's AND.
 */
export interface QualifierEmission {
  param: string | null;
  qParts: string[];
}

const NOTHING: QualifierEmission = { param: null, qParts: [] };

export function emitQualifier(
  qualifier: SearchQualifier,
  value: QualifierValue,
): QualifierEmission {
  const negated = value.negated && supportsNegation(qualifier);

  switch (qualifier.kind) {
    case "TEXT": {
      const text = value.text.trim();
      if (!text) return NOTHING;
      return { param: negated ? `-${text}` : text, qParts: [] };
    }

    case "BOOLEAN":
    case "FLAG":
      // Both are set purely by existing as a row; the operator picks the
      // polarity. The backend turns "false" into `-archived:true` / `-is:merged`
      // itself — a leading "-" here would be rejected as a malformed value.
      return { param: negated ? "false" : "true", qParts: [] };

    case "NUMBER_RANGE":
    case "DATE_RANGE": {
      const range = serializeRange(value, qualifier.kind);
      return range ? { param: range, qParts: [] } : NOTHING;
    }

    case "ENUM":
      if (!qualifier.repeatable) {
        const text = value.text.trim();
        if (!text) return NOTHING;
        return { param: negated ? `-${text}` : text, qParts: [] };
      }
      return emitList(qualifier, value, negated);

    case "MULTI_TEXT":
      return emitList(qualifier, value, negated);

    default:
      return NOTHING;
  }
}

function emitList(
  qualifier: SearchQualifier,
  value: QualifierValue,
  negated: boolean,
): QualifierEmission {
  const items = cleanList(value);
  if (items.length === 0) return NOTHING;

  // AND needs the qualifier repeated, which a single request parameter cannot
  // express — the backend always comma-joins one parameter. So AND rows are
  // rendered as raw fragments on `q` instead. `no:` is exempt: comma-joining it
  // ALREADY repeats, so it stays a plain parameter.
  if (items.length > 1 && effectiveJoin(qualifier, value) === "and" && !joinIsFixed(qualifier)) {
    const prefix = negated ? "-" : "";
    return {
      param: null,
      qParts: items.map((item) => `${prefix}${qualifier.githubQualifier}:${quoteIfNeeded(item)}`),
    };
  }

  // GitHub negates a qualifier per value, so a comma list negates element-wise.
  return { param: items.map((item) => (negated ? `-${item}` : item)).join(","), qParts: [] };
}

/**
 * Kept for the places that only care whether a filter is set at all (chips,
 * dirty checks); folds both emission channels into one comparable string.
 */
export function serializeQualifier(
  qualifier: SearchQualifier,
  value: QualifierValue,
): string | null {
  const { param, qParts } = emitQualifier(qualifier, value);
  if (param != null) return param;
  if (qParts.length > 0) return qParts.join(" ");
  return null;
}

export interface CollectedQualifiers {
  params: Record<string, string>;
  /** Raw GitHub fragments to append to the free-text `q`. */
  qParts: string[];
}

export function collectQualifiers(
  type: SearchTypeDescriptor,
  filters: FilterState,
): CollectedQualifiers {
  const params: Record<string, string> = {};
  const qParts: string[] = [];
  for (const qualifier of selectableQualifiers(type)) {
    if (!(qualifier.key in filters)) continue;
    const emission = emitQualifier(qualifier, filters[qualifier.key]);
    if (emission.param) params[qualifier.key] = emission.param;
    qParts.push(...emission.qParts);
  }
  return { params, qParts };
}

export interface ActiveFilter {
  key: string;
  label: string;
  /** What the user sees on the chip, e.g. `Stars: >=500`. */
  display: string;
  githubQualifier: string;
}

/** Plain-language rendering of one set filter, e.g. `Label: a OR b`. */
export function describeQualifier(
  qualifier: SearchQualifier,
  value: QualifierValue,
): string | null {
  if (serializeQualifier(qualifier, value) == null) return null;
  const not = value.negated && supportsNegation(qualifier) ? "not " : "";

  if (qualifier.kind === "BOOLEAN" || qualifier.kind === "FLAG") {
    return `${qualifier.label}: ${value.negated ? "no" : "yes"}`;
  }

  if (isListKind(qualifier)) {
    const items = cleanList(value);
    const separator = effectiveJoin(qualifier, value) === "and" ? " AND " : " OR ";
    return `${qualifier.label}: ${not}${items.join(separator)}`;
  }

  if (qualifier.kind === "NUMBER_RANGE" || qualifier.kind === "DATE_RANGE") {
    return `${qualifier.label}: ${serializeRange(value, qualifier.kind)}`;
  }

  return `${qualifier.label}: ${not}${value.text.trim()}`;
}

export function activeFilters(
  type: SearchTypeDescriptor,
  filters: FilterState,
): ActiveFilter[] {
  const active: ActiveFilter[] = [];
  for (const qualifier of selectableQualifiers(type)) {
    if (!(qualifier.key in filters)) continue;
    const display = describeQualifier(qualifier, filters[qualifier.key]);
    if (!display) continue;
    active.push({
      key: qualifier.key,
      label: qualifier.label,
      display,
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
  const { params, qParts } = collectQualifiers(type, form.filters);
  // AND fragments ride along on `q`, which the backend passes through raw. They
  // come after the user's keywords so the query still reads front-to-back.
  const q = [form.q.trim(), ...qParts].filter(Boolean).join(" ");

  return {
    q: q || undefined,
    owner: type.requiresRepositoryId ? form.owner.trim() || undefined : undefined,
    repo: type.requiresRepositoryId ? form.repo.trim() || undefined : undefined,
    // Topics has no sorts at all; sending a sort there would be a lie.
    sort: type.sorts.length > 0 && form.sort ? form.sort : undefined,
    order: type.supportsOrder && type.sorts.length > 0 && form.sort ? form.order : undefined,
    page,
    perPage: form.perPage,
    qualifiers: params,
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

/** Per-qualifier dirty check, so an unapplied row can be marked in place. */
export function qualifierDirty(
  qualifier: SearchQualifier,
  current: FilterState,
  applied: FilterState | null,
): boolean {
  // Presence matters as much as content: BOOLEAN and FLAG rows serialise to
  // "true" the moment they exist, so falling back to EMPTY_VALUE for a row that
  // is not in `applied` would compare equal and hide a genuine unapplied edit.
  const now = presentSerialization(qualifier, current);
  if (!applied) return now != null;
  return now !== presentSerialization(qualifier, applied);
}

function presentSerialization(qualifier: SearchQualifier, filters: FilterState): string | null {
  if (!(qualifier.key in filters)) return null;
  return serializeQualifier(qualifier, filters[qualifier.key]);
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
