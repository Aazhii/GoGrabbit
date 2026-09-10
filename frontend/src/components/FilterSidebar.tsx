import { useMemo } from "react";
import FilterRow from "./FilterRow";
import QualifierPicker from "./QualifierPicker";
import { presetIsActive, presetsFor } from "../lib/filterPresets";
import {
  qualifierDirty,
  selectableQualifiers,
  valueOf,
  type FilterState,
  type QualifierValue,
} from "../lib/searchFilters";
import type { SearchTypeDescriptor } from "../types";
import "./filters.css";

interface FilterSidebarProps {
  type: SearchTypeDescriptor;
  filters: FilterState;
  /** Filter state as of the last search — used to mark unapplied edits. */
  appliedFilters: FilterState | null;
  owner: string;
  repo: string;
  onOwnerRepoChange: (patch: { owner?: string; repo?: string }) => void;
  onFilterChange: (key: string, patch: Partial<QualifierValue>) => void;
  onAddFilter: (key: string) => void;
  onReplaceFilter: (key: string, nextKey: string) => void;
  onRemoveFilter: (key: string) => void;
  onApplyPreset: (filters: FilterState) => void;
  onClearAll: () => void;
  onApply: () => void;
  loading: boolean;
}

/**
 * A filter BUILDER, not a filter wall. Nothing is on screen until it has been
 * added — either by a one-click preset or through the "＋ Add filter" picker,
 * which keeps every one of the type's qualifiers reachable behind a search box.
 */
export default function FilterSidebar({
  type,
  filters,
  appliedFilters,
  owner,
  repo,
  onOwnerRepoChange,
  onFilterChange,
  onAddFilter,
  onReplaceFilter,
  onRemoveFilter,
  onApplyPreset,
  onClearAll,
  onApply,
  loading,
}: FilterSidebarProps) {
  const byKey = useMemo(
    () => new Map(selectableQualifiers(type).map((q) => [q.key, q])),
    [type],
  );

  // Insertion order of the record IS the row order: every key is a non-numeric
  // string, so JS preserves it, and no parallel array can drift out of sync.
  const rowKeys = useMemo(
    () => Object.keys(filters).filter((key) => byKey.has(key)),
    [filters, byKey],
  );
  const addedKeys = useMemo(() => new Set(rowKeys), [rowKeys]);

  const presets = useMemo(() => presetsFor(type), [type]);
  const hasQualifiers = byKey.size > 0;

  return (
    <form
      className="filter-sidebar fb"
      aria-label={`${type.label} filters`}
      onSubmit={(event) => {
        event.preventDefault();
        onApply();
      }}
    >
      {type.requiresRepositoryId && (
        <section className="fb-section">
          <h3 className="fb-section-title">Repository</h3>
          <p className="fb-note">
            Label search only works inside a single repository, and matches label
            names/descriptions by keyword — there are no other filters.
          </p>
          <div className="fb-field">
            <label htmlFor="search-scope-owner">Owner</label>
            <input
              id="search-scope-owner"
              className="fb-input"
              type="text"
              placeholder="facebook"
              value={owner}
              onChange={(e) => onOwnerRepoChange({ owner: e.target.value })}
            />
          </div>
          <div className="fb-field">
            <label htmlFor="search-scope-repo">Repository</label>
            <input
              id="search-scope-repo"
              className="fb-input"
              type="text"
              placeholder="react"
              value={repo}
              onChange={(e) => onOwnerRepoChange({ repo: e.target.value })}
            />
          </div>
        </section>
      )}

      {presets.length > 0 && (
        <section className="fb-section">
          <h3 className="fb-section-title">Quick starts</h3>
          <div className="fb-presets">
            {presets.map((preset) => {
              const active = presetIsActive(preset, filters);
              return (
                <button
                  key={preset.id}
                  type="button"
                  className={active ? "fb-preset fb-preset-on" : "fb-preset"}
                  aria-pressed={active}
                  title={preset.description}
                  disabled={loading}
                  onClick={() => onApplyPreset(preset.filters)}
                >
                  {preset.label}
                </button>
              );
            })}
          </div>
        </section>
      )}

      {hasQualifiers ? (
        <section className="fb-section">
          <h3 className="fb-section-title">
            Filters{rowKeys.length > 0 ? ` (${rowKeys.length})` : ""}
          </h3>

          {rowKeys.length === 0 ? (
            <p className="fb-empty">
              No filters yet. Pick a quick start above, or add one — all{" "}
              {byKey.size} {type.label.toLowerCase()} filters are in there.
            </p>
          ) : (
            <ul className="fb-rows">
              {rowKeys.map((key) => {
                const qualifier = byKey.get(key);
                if (!qualifier) return null;
                return (
                  <FilterRow
                    key={key}
                    type={type}
                    qualifier={qualifier}
                    value={valueOf(filters, key)}
                    added={addedKeys}
                    dirty={qualifierDirty(qualifier, filters, appliedFilters)}
                    onChange={(patch) => onFilterChange(key, patch)}
                    onReplace={(nextKey) => onReplaceFilter(key, nextKey)}
                    onRemove={() => onRemoveFilter(key)}
                  />
                );
              })}
            </ul>
          )}

          <QualifierPicker
            type={type}
            added={addedKeys}
            onSelect={onAddFilter}
            disabled={loading}
          />
        </section>
      ) : (
        !type.requiresRepositoryId && (
          <p className="fb-note">This search type takes keywords only — no filters.</p>
        )
      )}

      <div className="fb-actions">
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
