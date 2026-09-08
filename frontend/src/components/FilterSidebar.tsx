import QualifierControl from "./QualifierControl";
import {
  qualifierDirty,
  valueOf,
  type FilterState,
  type QualifierValue,
} from "../lib/searchFilters";
import type { SearchTypeDescriptor } from "../types";

interface FilterSidebarProps {
  type: SearchTypeDescriptor;
  filters: FilterState;
  /** Filter state as of the last search — used to mark unapplied edits. */
  appliedFilters: FilterState | null;
  owner: string;
  repo: string;
  onOwnerRepoChange: (patch: { owner?: string; repo?: string }) => void;
  onFilterChange: (key: string, patch: Partial<QualifierValue>) => void;
  onClearFilter: (key: string) => void;
  onClearAll: () => void;
  onApply: () => void;
  loading: boolean;
}

export default function FilterSidebar({
  type,
  filters,
  appliedFilters,
  owner,
  repo,
  onOwnerRepoChange,
  onFilterChange,
  onClearFilter,
  onClearAll,
  onApply,
  loading,
}: FilterSidebarProps) {
  // Groups come from the catalog in display order; a qualifier whose `group`
  // matches nothing still has to be reachable, so it lands in a trailing
  // "Other" section rather than disappearing.
  const known = new Set(type.groups.map((g) => g.key));
  const ungrouped = type.qualifiers.filter((q) => !known.has(q.group));
  const sections = [
    ...type.groups.map((group) => ({
      key: group.key,
      label: group.label,
      qualifiers: type.qualifiers.filter((q) => q.group === group.key),
    })),
    ...(ungrouped.length > 0
      ? [{ key: "__other", label: "Other", qualifiers: ungrouped }]
      : []),
  ].filter((section) => section.qualifiers.length > 0);

  return (
    <form
      className="filter-sidebar"
      aria-label={`${type.label} filters`}
      onSubmit={(event) => {
        event.preventDefault();
        onApply();
      }}
    >
      {type.requiresRepositoryId && (
        <section className="filter-group">
          <h3>Repository</h3>
          <p className="group-note">
            Label search only works inside a single repository, and matches label
            names/descriptions by keyword — there are no other filters.
          </p>
          <div className="qualifier">
            <label className="qualifier-label" htmlFor="search-scope-owner">
              Owner
            </label>
            <input
              id="search-scope-owner"
              type="text"
              placeholder="facebook"
              value={owner}
              onChange={(e) => onOwnerRepoChange({ owner: e.target.value })}
            />
          </div>
          <div className="qualifier">
            <label className="qualifier-label" htmlFor="search-scope-repo">
              Repository
            </label>
            <input
              id="search-scope-repo"
              type="text"
              placeholder="react"
              value={repo}
              onChange={(e) => onOwnerRepoChange({ repo: e.target.value })}
            />
          </div>
        </section>
      )}

      {sections.length === 0 && !type.requiresRepositoryId && (
        <p className="group-note">This search type takes keywords only — no filters.</p>
      )}

      {sections.map((section) => (
        <section key={section.key} className="filter-group">
          <h3>{section.label}</h3>
          {section.qualifiers.map((qualifier) => (
            <QualifierControl
              key={qualifier.key}
              qualifier={qualifier}
              value={valueOf(filters, qualifier.key)}
              dirty={qualifierDirty(qualifier, filters, appliedFilters)}
              idPrefix={`qf-${type.slug}`}
              onChange={(patch) => onFilterChange(qualifier.key, patch)}
              onClear={() => onClearFilter(qualifier.key)}
            />
          ))}
        </section>
      ))}

      <div className="filter-actions">
        <button type="submit" className="primary" disabled={loading}>
          {loading ? "Searching…" : "Apply filters"}
        </button>
        <button type="button" onClick={onClearAll} disabled={loading}>
          Clear all
        </button>
      </div>
    </form>
  );
}
