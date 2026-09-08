import {
  CREATED_PRESETS,
  PRESET_LABELS,
  SORT_OPTIONS,
  type CreatedPreset,
  type SearchFormValues,
  type SortChoice,
} from "../lib/searchForm";
import type { IssueStateFilter } from "../types";

interface SearchFormProps {
  values: SearchFormValues;
  onChange: (patch: Partial<SearchFormValues>) => void;
  onSubmit: () => void;
  onReset: () => void;
  loading: boolean;
}

export default function SearchForm({ values, onChange, onSubmit, onReset, loading }: SearchFormProps) {
  function toggleLabel(label: string) {
    const next = values.presetLabels.includes(label)
      ? values.presetLabels.filter((l) => l !== label)
      : [...values.presetLabels, label];
    onChange({ presetLabels: next });
  }

  return (
    <form
      className="search-form"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <div className="field field-wide">
        <label htmlFor="search-q">Keywords</label>
        <input
          id="search-q"
          type="search"
          placeholder="optional — words in the issue title or body"
          value={values.q}
          onChange={(e) => onChange({ q: e.target.value })}
        />
      </div>

      <fieldset className="field field-wide labels-field">
        <legend>Labels</legend>
        <div className="chip-row">
          {PRESET_LABELS.map((label) => {
            const id = `label-${label.replace(/\s+/g, "-")}`;
            const checked = values.presetLabels.includes(label);
            return (
              <span key={label} className={checked ? "chip chip-on" : "chip"}>
                <input
                  id={id}
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggleLabel(label)}
                />
                <label htmlFor={id}>{label}</label>
              </span>
            );
          })}
        </div>
        <label htmlFor="search-custom-labels" className="sub-label">
          Other labels (comma separated)
        </label>
        <input
          id="search-custom-labels"
          type="text"
          placeholder="e.g. beginner-friendly, up-for-grabs"
          value={values.customLabels}
          onChange={(e) => onChange({ customLabels: e.target.value })}
        />
      </fieldset>

      <div className="field">
        <label htmlFor="search-repository">Repository</label>
        <input
          id="search-repository"
          type="text"
          placeholder="owner/repo — any repo, not just watched ones"
          value={values.repository}
          onChange={(e) => onChange({ repository: e.target.value })}
        />
      </div>

      <div className="field">
        <label htmlFor="search-state">State</label>
        <select
          id="search-state"
          value={values.state}
          onChange={(e) => onChange({ state: e.target.value as IssueStateFilter })}
        >
          <option value="open">Open</option>
          <option value="closed">Closed</option>
          <option value="all">Any</option>
        </select>
      </div>

      <div className="field">
        <label htmlFor="search-created">Created</label>
        <select
          id="search-created"
          value={values.created}
          onChange={(e) => onChange({ created: e.target.value as CreatedPreset })}
        >
          {CREATED_PRESETS.map((preset) => (
            <option key={preset.value} value={preset.value}>
              {preset.label}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor="search-sort">Sort by</label>
        <select
          id="search-sort"
          value={values.sort}
          onChange={(e) => onChange({ sort: e.target.value as SortChoice })}
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      {values.created === "custom" && (
        <>
          <div className="field">
            <label htmlFor="search-created-from">Created from</label>
            <input
              id="search-created-from"
              type="date"
              value={values.createdFrom}
              onChange={(e) => onChange({ createdFrom: e.target.value })}
            />
          </div>
          <div className="field">
            <label htmlFor="search-created-to">Created to</label>
            <input
              id="search-created-to"
              type="date"
              value={values.createdTo}
              onChange={(e) => onChange({ createdTo: e.target.value })}
            />
          </div>
        </>
      )}

      <div className="field">
        <label htmlFor="search-per-page">Results per page</label>
        <select
          id="search-per-page"
          value={String(values.perPage)}
          onChange={(e) => onChange({ perPage: Number(e.target.value) })}
        >
          {[10, 30, 50, 100].map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
      </div>

      <div className="search-actions">
        <button type="submit" className="primary" disabled={loading}>
          {loading ? "Searching…" : "Search issues"}
        </button>
        <button type="button" onClick={onReset} disabled={loading}>
          Reset filters
        </button>
      </div>
    </form>
  );
}
