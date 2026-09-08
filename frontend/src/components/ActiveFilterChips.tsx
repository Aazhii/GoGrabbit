import type { ActiveFilter } from "../lib/searchFilters";

interface ActiveFilterChipsProps {
  filters: ActiveFilter[];
  /** Free-text term, shown as a removable chip like any other filter. */
  term: string;
  onRemove: (key: string) => void;
  onRemoveTerm: () => void;
  onClearAll: () => void;
}

export default function ActiveFilterChips({
  filters,
  term,
  onRemove,
  onRemoveTerm,
  onClearAll,
}: ActiveFilterChipsProps) {
  if (filters.length === 0 && !term) return null;

  return (
    <div className="active-filters">
      <span className="active-filters-label">Active:</span>
      <ul>
        {term && (
          <li>
            <span>text: {term}</span>
            <button type="button" aria-label={`Remove keyword filter ${term}`} onClick={onRemoveTerm}>
              ×
            </button>
          </li>
        )}
        {filters.map((filter) => (
          <li key={filter.key}>
            <span title={`${filter.githubQualifier}: qualifier`}>{filter.display}</span>
            <button
              type="button"
              aria-label={`Remove ${filter.label} filter`}
              onClick={() => onRemove(filter.key)}
            >
              ×
            </button>
          </li>
        ))}
      </ul>
      {filters.length + (term ? 1 : 0) > 1 && (
        <button type="button" className="link-button" onClick={onClearAll}>
          Clear all
        </button>
      )}
    </div>
  );
}
