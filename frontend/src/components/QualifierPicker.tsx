import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { selectableQualifiers } from "../lib/searchFilters";
import type { SearchQualifier, SearchTypeDescriptor } from "../types";

interface QualifierPickerProps {
  type: SearchTypeDescriptor;
  /** Keys that already have a row — listed, but not addable a second time. */
  added: Set<string>;
  onSelect: (key: string) => void;
  disabled?: boolean;
}

interface Option {
  qualifier: SearchQualifier;
  groupLabel: string;
  added: boolean;
}

function groupLabels(type: SearchTypeDescriptor): Map<string, string> {
  return new Map(type.groups.map((g) => [g.key, g.label]));
}

function matches(option: Option, query: string): boolean {
  if (!query) return true;
  const needle = query.toLowerCase();
  const haystack = [
    option.qualifier.label,
    option.qualifier.help ?? "",
    option.qualifier.githubQualifier,
    option.qualifier.key,
    option.groupLabel,
  ]
    .join(" ")
    .toLowerCase();
  return haystack.includes(needle);
}

/**
 * "＋ Add filter" — the one control that stands between a clean sidebar and all
 * 42 qualifiers. Everything stays reachable through the search box; nothing is
 * on screen until it has been chosen.
 */
export default function QualifierPicker({
  type,
  added,
  onSelect,
  disabled,
}: QualifierPickerProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const [placement, setPlacement] = useState<{
    left: number;
    top?: number;
    bottom?: number;
    width: number;
    maxHeight: number;
  } | null>(null);

  const baseId = useId();
  const listId = `${baseId}-list`;
  const triggerRef = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);

  const labels = useMemo(() => groupLabels(type), [type]);

  const allOptions: Option[] = useMemo(
    () =>
      selectableQualifiers(type).map((qualifier) => ({
        qualifier,
        groupLabel: labels.get(qualifier.group) ?? "Other",
        added: added.has(qualifier.key),
      })),
    [type, labels, added],
  );

  const visible = useMemo(
    () => allOptions.filter((option) => matches(option, query)),
    [allOptions, query],
  );

  /** Only not-yet-added options are selectable, so only they get arrow focus. */
  const selectable = useMemo(() => visible.filter((option) => !option.added), [visible]);

  // Section the visible options in catalog group order, keeping any qualifier
  // whose group is unknown in a trailing "Other" bucket rather than dropping it.
  const sections = useMemo(() => {
    const order = [...type.groups.map((g) => g.label), "Other"];
    const bucket = new Map<string, Option[]>();
    for (const option of visible) {
      const list = bucket.get(option.groupLabel);
      if (list) list.push(option);
      else bucket.set(option.groupLabel, [option]);
    }
    return order
      .filter((label) => bucket.has(label))
      .map((label) => ({ label, options: bucket.get(label) ?? [] }));
  }, [visible, type.groups]);

  useEffect(() => {
    if (!open) return;
    inputRef.current?.focus();
  }, [open]);

  // Clicking anywhere outside the popover dismisses it. `pointerdown` (not
  // click) so a drag that starts outside also closes, and so the trigger's own
  // click still toggles rather than reopening what this just closed.
  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      const target = event.target as Node;
      if (popoverRef.current?.contains(target)) return;
      if (triggerRef.current?.contains(target)) return;
      setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  // The sidebar is a sticky scroll container (overflow-y: auto), which CLIPS an
  // absolutely-positioned child — the popover was being cut to a sliver. Fixed
  // positioning escapes any ancestor overflow, so measure the trigger and place
  // it in viewport coordinates. Recomputed on scroll and resize so it tracks.
  useLayoutEffect(() => {
    if (!open) return;

    function place() {
      const trigger = triggerRef.current;
      if (!trigger) return;
      const r = trigger.getBoundingClientRect();
      const margin = 8;
      const width = Math.max(r.width, 300);
      // Flip above the trigger when there is more room up than down.
      const below = window.innerHeight - r.bottom - margin;
      const above = r.top - margin;
      const flip = below < 240 && above > below;
      const maxHeight = Math.max(200, Math.min(440, flip ? above : below));
      setPlacement({
        left: Math.min(Math.max(margin, r.left), window.innerWidth - width - margin),
        top: flip ? undefined : r.bottom + 6,
        bottom: flip ? window.innerHeight - r.top + 6 : undefined,
        width,
        maxHeight,
      });
    }

    place();
    window.addEventListener("scroll", place, true);
    window.addEventListener("resize", place);
    return () => {
      window.removeEventListener("scroll", place, true);
      window.removeEventListener("resize", place);
    };
  }, [open]);

  // Keep the arrow-key selection inside the scroll box.
  useEffect(() => {
    if (!open) return;
    const active = selectable[activeIndex];
    if (!active) return;
    document
      .getElementById(`${baseId}-opt-${active.qualifier.key}`)
      ?.scrollIntoView({ block: "nearest" });
  }, [open, activeIndex, selectable, baseId]);

  function close(returnFocus: boolean) {
    setOpen(false);
    setQuery("");
    if (returnFocus) triggerRef.current?.focus();
  }

  function choose(option: Option) {
    if (option.added) return;
    onSelect(option.qualifier.key);
    close(true);
  }

  function onKeyDown(event: React.KeyboardEvent) {
    switch (event.key) {
      case "Escape":
        event.preventDefault();
        close(true);
        break;
      case "ArrowDown":
        event.preventDefault();
        setActiveIndex((i) => (selectable.length === 0 ? 0 : (i + 1) % selectable.length));
        break;
      case "ArrowUp":
        event.preventDefault();
        setActiveIndex((i) =>
          selectable.length === 0 ? 0 : (i - 1 + selectable.length) % selectable.length,
        );
        break;
      case "Home":
        event.preventDefault();
        setActiveIndex(0);
        break;
      case "End":
        event.preventDefault();
        setActiveIndex(Math.max(0, selectable.length - 1));
        break;
      case "Enter": {
        event.preventDefault();
        const option = selectable[activeIndex];
        if (option) choose(option);
        break;
      }
      case "Tab":
        // No focus trap to fight with: leaving the popover simply dismisses it.
        close(false);
        break;
      default:
        break;
    }
  }

  const activeOption = selectable[activeIndex];

  return (
    <div className="fb-picker">
      <button
        ref={triggerRef}
        type="button"
        className="fb-add-button"
        aria-expanded={open}
        aria-haspopup="dialog"
        disabled={disabled}
        onClick={() => {
          if (open) {
            close(false);
          } else {
            setActiveIndex(0);
            setOpen(true);
          }
        }}
      >
        <span aria-hidden="true">＋</span> Add filter
      </button>

      {open && createPortal(
        <div
          ref={popoverRef}
          className="fb-popover"
          style={
            placement
              ? {
                  left: placement.left,
                  top: placement.top,
                  bottom: placement.bottom,
                  width: placement.width,
                  maxHeight: placement.maxHeight,
                  ["--fb-popover-max-h" as string]: `${placement.maxHeight - 62}px`,
                }
              : { visibility: "hidden" }
          }
          role="dialog"
          aria-label={`Add a ${type.label.toLowerCase()} filter`}
          onKeyDown={onKeyDown}
        >
          <div className="fb-popover-search">
            <label className="sr-only" htmlFor={`${baseId}-search`}>
              Search filters
            </label>
            <input
              id={`${baseId}-search`}
              ref={inputRef}
              type="text"
              className="fb-input"
              placeholder="Search filters — label, stars, created…"
              role="combobox"
              aria-expanded="true"
              aria-controls={listId}
              aria-autocomplete="list"
              aria-activedescendant={
                activeOption ? `${baseId}-opt-${activeOption.qualifier.key}` : undefined
              }
              value={query}
              onChange={(e) => {
                // Reset from the event, not an effect: a stale highlight would
                // otherwise survive one render past the list it pointed into.
                setQuery(e.target.value);
                setActiveIndex(0);
              }}
            />
          </div>

          <div className="fb-popover-list" id={listId} role="listbox" aria-label="Filters">
            {sections.length === 0 && (
              <p className="fb-popover-empty">No filter matches “{query}”.</p>
            )}
            {sections.map((section) => (
              <div key={section.label} role="group" aria-label={section.label}>
                <p className="fb-popover-group">{section.label}</p>
                {section.options.map((option) => {
                  const isActive = activeOption?.qualifier.key === option.qualifier.key;
                  return (
                    <div
                      key={option.qualifier.key}
                      id={`${baseId}-opt-${option.qualifier.key}`}
                      role="option"
                      aria-selected={isActive}
                      aria-disabled={option.added || undefined}
                      className={
                        "fb-option" +
                        (isActive ? " fb-option-active" : "") +
                        (option.added ? " fb-option-added" : "")
                      }
                      onMouseMove={() => {
                        const index = selectable.indexOf(option);
                        if (index >= 0 && index !== activeIndex) setActiveIndex(index);
                      }}
                      onClick={() => choose(option)}
                    >
                      <span className="fb-option-head">
                        <span className="fb-option-label">{option.qualifier.label}</span>
                        <code className="fb-option-code">
                          {option.qualifier.githubQualifier}:
                        </code>
                        {option.added && <span className="fb-option-badge">Added</span>}
                      </span>
                      {option.qualifier.help && (
                        <span className="fb-option-help">{option.qualifier.help}</span>
                      )}
                    </div>
                  );
                })}
              </div>
            ))}
          </div>
        </div>,
        document.body,
      )}
    </div>
  );
}
