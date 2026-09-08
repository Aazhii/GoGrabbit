import { useId, useMemo, useRef } from "react";
import QualifierValueEditor from "./QualifierControl";
import {
  comparatorsFor,
  effectiveJoin,
  isListKind,
  joinIsFixed,
  selectableQualifiers,
  supportsJoinToggle,
  supportsNegation,
  type Comparator,
  type QualifierValue,
} from "../lib/searchFilters";
import type { SearchQualifier, SearchTypeDescriptor } from "../types";

interface FilterRowProps {
  type: SearchTypeDescriptor;
  qualifier: SearchQualifier;
  value: QualifierValue;
  /** Keys that already have a row — they can't be swapped to from this one. */
  added: Set<string>;
  /** Set but not yet searched — the row is marked in place. */
  dirty: boolean;
  onChange: (patch: Partial<QualifierValue>) => void;
  onReplace: (nextKey: string) => void;
  onRemove: () => void;
}

/**
 * One filter, read as a sentence:
 *
 *   [Label ▾] [is ▾] [good first issue ×] OR [help wanted ×] [＋]   [×]
 *
 * The row owns the qualifier, the operator and the OR/AND join; the value
 * editor owns only the operand(s).
 */
export default function FilterRow({
  type,
  qualifier,
  value,
  added,
  dirty,
  onChange,
  onReplace,
  onRemove,
}: FilterRowProps) {
  const baseId = useId();
  const focusRef = useRef<HTMLInputElement | HTMLSelectElement | null>(null);

  const isRange = qualifier.kind === "NUMBER_RANGE" || qualifier.kind === "DATE_RANGE";
  const multi = isListKind(qualifier);
  const showJoin = multi && value.list.length > 1;
  const join = effectiveJoin(qualifier, value);

  // Grouped list for the swap dropdown: every qualifier of this type except the
  // ones already used by another row.
  const swapGroups = useMemo(() => {
    const labels = new Map(type.groups.map((g) => [g.key, g.label]));
    const order = [...type.groups.map((g) => g.label), "Other"];
    const bucket = new Map<string, SearchQualifier[]>();
    for (const candidate of selectableQualifiers(type)) {
      if (candidate.key !== qualifier.key && added.has(candidate.key)) continue;
      const label = labels.get(candidate.group) ?? "Other";
      const list = bucket.get(label);
      if (list) list.push(candidate);
      else bucket.set(label, [candidate]);
    }
    return order
      .filter((label) => bucket.has(label))
      .map((label) => ({ label, options: bucket.get(label) ?? [] }));
  }, [type, added, qualifier.key]);

  return (
    <li className={dirty ? "fb-row fb-row-dirty" : "fb-row"}>
      <div className="fb-row-line">
        <label className="sr-only" htmlFor={`${baseId}-key`}>
          Filter field
        </label>
        <select
          id={`${baseId}-key`}
          className="fb-select fb-select-key"
          value={qualifier.key}
          onChange={(e) => onReplace(e.target.value)}
        >
          {swapGroups.map((group) => (
            <optgroup key={group.label} label={group.label}>
              {group.options.map((option) => (
                <option key={option.key} value={option.key}>
                  {option.label}
                </option>
              ))}
            </optgroup>
          ))}
        </select>

        {isRange ? (
          <>
            <label className="sr-only" htmlFor={`${baseId}-op`}>
              {qualifier.label} comparison
            </label>
            <select
              id={`${baseId}-op`}
              className="fb-select fb-select-op"
              /* A comparator the kind does not offer (a date window left on a
                 numeric row) would render blank; fall back to a valid one. */
              value={
                comparatorsFor(qualifier.kind).some((c) => c.value === value.comparator)
                  ? value.comparator
                  : "gte"
              }
              onChange={(e) => onChange({ comparator: e.target.value as Comparator })}
            >
              {comparatorsFor(qualifier.kind).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </>
        ) : supportsNegation(qualifier) ? (
          <>
            <label className="sr-only" htmlFor={`${baseId}-op`}>
              {qualifier.label} operator
            </label>
            <select
              id={`${baseId}-op`}
              className="fb-select fb-select-op"
              value={value.negated ? "not" : "is"}
              onChange={(e) => onChange({ negated: e.target.value === "not" })}
            >
              <option value="is">is</option>
              <option value="not">is not</option>
            </select>
          </>
        ) : (
          <span className="fb-op-static">is</span>
        )}

        <QualifierValueEditor
          qualifier={qualifier}
          value={value}
          id={`${baseId}-value`}
          focusRef={focusRef}
          onChange={onChange}
        />

        {multi && (
          <button
            type="button"
            className="fb-icon-button"
            title={`Add another ${qualifier.label} value`}
            aria-label={`Add another ${qualifier.label} value`}
            onClick={() => focusRef.current?.focus()}
          >
            <span aria-hidden="true">＋</span>
          </button>
        )}

        <button
          type="button"
          className="fb-icon-button fb-icon-remove"
          aria-label={`Remove the ${qualifier.label} filter`}
          onClick={onRemove}
        >
          <span aria-hidden="true">×</span>
        </button>
      </div>

      {showJoin && supportsJoinToggle(qualifier) && (
        <fieldset className="fb-join">
          <legend className="fb-join-legend">
            {value.list.length} values — combine with
          </legend>
          {(
            [
              { mode: "or" as const, label: "OR", plain: "matches any" },
              { mode: "and" as const, label: "AND", plain: "matches all" },
            ]
          ).map((option) => {
            const id = `${baseId}-join-${option.mode}`;
            return (
              <span
                key={option.mode}
                className={join === option.mode ? "fb-join-option fb-join-on" : "fb-join-option"}
              >
                <input
                  id={id}
                  type="radio"
                  name={`${baseId}-join`}
                  checked={join === option.mode}
                  onChange={() => onChange({ join: option.mode })}
                />
                <label htmlFor={id}>
                  <strong>{option.label}</strong> <span>{option.plain}</span>
                </label>
              </span>
            );
          })}
        </fieldset>
      )}

      {showJoin && joinIsFixed(qualifier) && (
        <p className="fb-row-note">
          GitHub only understands <code>{qualifier.githubQualifier}:</code> as separate terms, so
          these always match <strong>all</strong> (AND).
        </p>
      )}

      {dirty && <p className="fb-row-note fb-row-note-dirty">Not applied yet</p>}
    </li>
  );
}
