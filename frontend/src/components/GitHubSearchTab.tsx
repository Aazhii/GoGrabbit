import { useCallback, useMemo, useState } from "react";
import ActiveFilterChips from "./ActiveFilterChips";
import EmptyState from "./EmptyState";
import ErrorNotice from "./ErrorNotice";
import FilterSidebar from "./FilterSidebar";
import SearchResultsPanel from "./SearchResultsPanel";
import SearchTypeTabs from "./SearchTypeTabs";
import Spinner from "./Spinner";
import { useGitHubSearch } from "../hooks/useGitHubSearch";
import { useSearchCatalog } from "../hooks/useSearchCatalog";
import {
  EMPTY_VALUE,
  defaultComparator,
  activeFilters,
  formFingerprint,
  initialFormState,
  splitOwnerRepo,
  toRequestParams,
  type FilterState,
  type QualifierValue,
  type SearchFormState,
} from "../lib/searchFilters";
import type { SearchTypeDescriptor, SearchTypeSlug } from "../types";

type FormsBySlug = Partial<Record<SearchTypeSlug, SearchFormState>>;

export default function GitHubSearchTab() {
  const { catalog, loading: catalogLoading, error: catalogError, reload } = useSearchCatalog();
  const search = useGitHubSearch();

  const types = useMemo(() => catalog?.types ?? [], [catalog]);
  const [slug, setSlug] = useState<SearchTypeSlug | null>(null);
  // Land on issues rather than whatever the catalog happens to list first: the
  // freshness filters that make this product useful live on that tab, and opening
  // on repositories hid them behind a tab switch.
  const defaultSlug: SearchTypeSlug | null =
    types.find((t) => t.slug === "issues")?.slug ?? types[0]?.slug ?? null;
  const activeSlug: SearchTypeSlug | null = slug ?? defaultSlug;
  const type = useMemo(
    () => types.find((t) => t.slug === activeSlug) ?? null,
    [types, activeSlug],
  );

  const [forms, setForms] = useState<FormsBySlug>({});
  /** Form state as of the last submit, per type — drives "not applied yet". */
  const [appliedForms, setAppliedForms] = useState<FormsBySlug>({});
  const [validation, setValidation] = useState<string | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const form: SearchFormState = (activeSlug && forms[activeSlug]) || initialFormState(type);
  const applied = activeSlug ? (appliedForms[activeSlug] ?? null) : null;

  const patchForm = useCallback(
    (patch: Partial<SearchFormState>) => {
      if (!activeSlug) return;
      setForms((current) => {
        const base = current[activeSlug] ?? initialFormState(type);
        return { ...current, [activeSlug]: { ...base, ...patch } };
      });
    },
    [activeSlug, type],
  );

  const patchFilter = useCallback(
    (key: string, patch: Partial<QualifierValue>) => {
      if (!activeSlug) return;
      setForms((current) => {
        const base = current[activeSlug] ?? initialFormState(type);
        const filters: FilterState = {
          ...base.filters,
          [key]: { ...EMPTY_VALUE, ...base.filters[key], ...patch },
        };
        return { ...current, [activeSlug]: { ...base, filters } };
      });
    },
    [activeSlug, type],
  );

  function validate(descriptor: SearchTypeDescriptor, values: SearchFormState): string | null {
    if (descriptor.requiresSearchTerm && !values.q.trim()) {
      return `${descriptor.label} search needs at least one keyword — GitHub rejects a ${descriptor.label.toLowerCase()} query built only from qualifiers.`;
    }
    if (descriptor.requiresRepositoryId && (!values.owner.trim() || !values.repo.trim())) {
      return "Label search is scoped to a single repository — fill in both the owner and the repository name.";
    }
    return null;
  }

  const submit = useCallback(
    (values: SearchFormState, page: number) => {
      if (!type || !activeSlug) return;
      const problem = validate(type, values);
      setValidation(problem);
      if (problem) return;

      setAppliedForms((current) => ({ ...current, [activeSlug]: values }));
      search.run(activeSlug, toRequestParams(type, values, page));
    },
    [type, activeSlug, search],
  );

  function selectType(next: SearchTypeSlug) {
    if (next === activeSlug) return;
    setSlug(next);
    setValidation(null);
    // Each type has its own results; a stale list under a new tab would lie.
    search.reset();
  }

  function goToPage(page: number) {
    if (!type || !activeSlug) return;
    // Page with the filters the visible results came from, not whatever the
    // sidebar holds now — the user may have edited without re-searching.
    const base = search.lastRun;
    if (base && base.slug === activeSlug) search.run(activeSlug, { ...base.params, page });
    else submit(form, page);
  }

  function changeSort(sort: string) {
    patchForm({ sort });
    if (!search.pristine) submit({ ...form, sort }, 1);
  }

  function changeOrder(order: "asc" | "desc") {
    patchForm({ order });
    if (!search.pristine) submit({ ...form, order }, 1);
  }

  /** Adds an empty row. Presence in the record IS the row — see searchFilters. */
  function addFilter(key: string) {
    if (!activeSlug || key in form.filters) return;
    // A date row opens on "in the last 7 days" rather than an empty absolute
    // date, because that is the question people actually come here to ask.
    const kind = type?.qualifiers.find((q) => q.key === key)?.kind;
    patchFilter(
      key,
      kind === "DATE_RANGE" ? { comparator: defaultComparator(kind), from: "7" } : {},
    );
  }

  /**
   * Swapping the qualifier on an existing row keeps the row where it is, so the
   * list doesn't reshuffle under the cursor — hence the rebuild rather than a
   * delete + re-add, which would move it to the end.
   */
  function replaceFilter(key: string, nextKey: string) {
    if (!activeSlug || key === nextKey) return;
    const filters: FilterState = {};
    for (const [existing, value] of Object.entries(form.filters)) {
      if (existing === key) filters[nextKey] = { ...EMPTY_VALUE };
      else if (existing !== nextKey) filters[existing] = value;
    }
    setForms((current) => ({ ...current, [activeSlug]: { ...form, filters } }));
  }

  /** Removing a row deletes the key outright — an empty row is still a row. */
  function removeFilter(key: string) {
    if (!activeSlug) return;
    const filters = { ...form.filters };
    delete filters[key];
    const next: SearchFormState = { ...form, filters };
    setForms((current) => ({ ...current, [activeSlug]: next }));
    if (!search.pristine) submit(next, 1);
  }

  /** A preset replaces the whole filter set and searches straight away. */
  function applyPreset(filters: FilterState) {
    if (!activeSlug) return;
    const next: SearchFormState = { ...form, filters: { ...filters } };
    setForms((current) => ({ ...current, [activeSlug]: next }));
    submit(next, 1);
  }

  function removeTerm() {
    if (!activeSlug) return;
    const next: SearchFormState = { ...form, q: "" };
    setForms((current) => ({ ...current, [activeSlug]: next }));
    // A type that requires a keyword can't re-run without one — just clear the
    // box and let the validation message explain itself on the next submit.
    if (!search.pristine && type && !type.requiresSearchTerm) submit(next, 1);
  }

  function clearAll() {
    if (!activeSlug) return;
    const next = initialFormState(type);
    setForms((current) => ({ ...current, [activeSlug]: next }));
    setValidation(null);
    search.reset();
    setAppliedForms((current) => {
      const remaining = { ...current };
      delete remaining[activeSlug];
      return remaining;
    });
  }

  function setOwnerRepo(patch: { owner?: string; repo?: string }) {
    // Pasting "owner/repo" (or a github.com URL) into either box splits itself.
    if (patch.owner?.includes("/")) {
      const { owner, repo } = splitOwnerRepo(patch.owner);
      patchForm({ owner, repo: repo || form.repo });
      return;
    }
    patchForm(patch);
  }

  // One search = one GitHub request, so we never fan out across types: only the
  // tab you actually searched can honestly show a count.
  const counts: Partial<Record<SearchTypeSlug, number>> =
    activeSlug && search.data ? { [activeSlug]: search.data.totalCount } : {};

  const chips = type ? activeFilters(type, form.filters) : [];
  const unappliedEdits =
    type != null &&
    applied != null &&
    formFingerprint(type, form) !== formFingerprint(type, applied);

  if (catalogLoading) {
    return (
      <section className="search-shell-loading">
        <Spinner label="Loading search options…" />
      </section>
    );
  }

  if (catalogError) {
    return (
      <section>
        <ErrorNotice error={catalogError} onRetry={reload} />
      </section>
    );
  }

  if (!catalog || !type || !activeSlug) {
    return (
      <section>
        <EmptyState title="Search is unavailable">
          <p>The backend returned an empty search catalog, so there is nothing to search yet.</p>
        </EmptyState>
      </section>
    );
  }

  return (
    <div className="search-shell">
      <form
        className="query-bar"
        onSubmit={(event) => {
          event.preventDefault();
          submit(form, 1);
        }}
      >
        <label className="sr-only" htmlFor="search-term">
          Search GitHub
        </label>
        <input
          id="search-term"
          type="search"
          placeholder={
            type.requiresSearchTerm
              ? `Required — keywords to search ${type.label.toLowerCase()} for`
              : `Search ${type.label.toLowerCase()}…`
          }
          value={form.q}
          onChange={(e) => patchForm({ q: e.target.value })}
        />
        <button type="submit" className="primary" disabled={search.loading}>
          {search.loading ? "Searching…" : "Search"}
        </button>
      </form>

      <SearchTypeTabs types={types} active={activeSlug} counts={counts} onSelect={selectType} />

      {validation && (
        <p className="validation-note" role="alert">
          {validation}
        </p>
      )}

      {type.requiresAuth && (
        <p className="auth-note">
          {type.label} search requires a GitHub token on the backend
          (<code>GITHUB_TOKEN</code>), and runs in its own rate-limit bucket of about 10 requests a
          minute — far tighter than the other search types.
        </p>
      )}

      <button
        type="button"
        className="sidebar-toggle"
        aria-expanded={sidebarOpen}
        aria-controls="filter-sidebar"
        onClick={() => setSidebarOpen((open) => !open)}
      >
        {sidebarOpen ? "Hide filters" : `Show filters${chips.length > 0 ? ` (${chips.length})` : ""}`}
      </button>

      <div className="search-layout">
        <aside
          id="filter-sidebar"
          className={sidebarOpen ? "search-sidebar search-sidebar-open" : "search-sidebar"}
        >
          <FilterSidebar
            type={type}
            filters={form.filters}
            appliedFilters={applied?.filters ?? null}
            owner={form.owner}
            repo={form.repo}
            onOwnerRepoChange={setOwnerRepo}
            onFilterChange={patchFilter}
            onAddFilter={addFilter}
            onReplaceFilter={replaceFilter}
            onRemoveFilter={removeFilter}
            onApplyPreset={applyPreset}
            onClearAll={clearAll}
            onApply={() => submit(form, 1)}
            loading={search.loading}
          />
        </aside>

        <div className="search-main" id="search-panel" role="tabpanel" aria-labelledby={`type-tab-${activeSlug}`}>
          <ActiveFilterChips
            filters={chips}
            term={applied?.q.trim() ? applied.q.trim() : ""}
            onRemove={removeFilter}
            onRemoveTerm={removeTerm}
            onClearAll={clearAll}
          />

          {unappliedEdits && (
            <p className="unapplied-note">
              You have unapplied filter changes.
              <button type="button" className="link-button" onClick={() => submit(form, 1)}>
                Search again
              </button>
            </p>
          )}

          <SearchResultsPanel
            type={type}
            loading={search.loading}
            pristine={search.pristine}
            error={search.error}
            data={search.data}
            sort={form.sort}
            order={form.order}
            onSortChange={changeSort}
            onOrderChange={changeOrder}
            onRetry={() => {
              const last = search.lastRun;
              if (last) search.run(last.slug, last.params);
              else submit(form, 1);
            }}
            onPageChange={goToPage}
          />
        </div>
      </div>
    </div>
  );
}
