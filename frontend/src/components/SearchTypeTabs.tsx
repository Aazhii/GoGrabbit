import { useRef, type KeyboardEvent } from "react";
import type { SearchTypeDescriptor, SearchTypeSlug } from "../types";

interface SearchTypeTabsProps {
  types: SearchTypeDescriptor[];
  active: SearchTypeSlug;
  /** Result counts keyed by slug, shown on the tab once a type has been run. */
  counts: Partial<Record<SearchTypeSlug, number>>;
  onSelect: (slug: SearchTypeSlug) => void;
}

export default function SearchTypeTabs({ types, active, counts, onSelect }: SearchTypeTabsProps) {
  const listRef = useRef<HTMLDivElement | null>(null);

  // Roving-tabindex keyboard model: only the selected tab is tabbable, arrows
  // move between them and select as they go (an "automatic activation" tablist).
  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const keys = ["ArrowLeft", "ArrowRight", "Home", "End"];
    if (!keys.includes(event.key)) return;
    event.preventDefault();

    const index = types.findIndex((t) => t.slug === active);
    let next = index;
    if (event.key === "ArrowLeft") next = (index - 1 + types.length) % types.length;
    if (event.key === "ArrowRight") next = (index + 1) % types.length;
    if (event.key === "Home") next = 0;
    if (event.key === "End") next = types.length - 1;

    const target = types[next];
    if (!target) return;
    onSelect(target.slug);
    listRef.current?.querySelector<HTMLButtonElement>(`#type-tab-${target.slug}`)?.focus();
  }

  return (
    <div
      className="type-tabs"
      role="tablist"
      aria-label="Search types"
      ref={listRef}
      onKeyDown={handleKeyDown}
    >
      {types.map((type) => {
        const selected = type.slug === active;
        const count = counts[type.slug];
        return (
          <button
            key={type.slug}
            id={`type-tab-${type.slug}`}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls="search-panel"
            tabIndex={selected ? 0 : -1}
            className={selected ? "type-tab type-tab-active" : "type-tab"}
            onClick={() => onSelect(type.slug)}
          >
            <span>{type.label}</span>
            {count != null && <span className="type-tab-count">{count.toLocaleString()}</span>}
          </button>
        );
      })}
    </div>
  );
}
