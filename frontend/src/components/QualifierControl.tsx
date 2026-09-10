import { useState, type RefObject } from "react";
import { RELATIVE_WINDOWS, type QualifierValue } from "../lib/searchFilters";
import type { SearchQualifier } from "../types";

/**
 * The value half of a filter row. The row itself owns the qualifier picker and
 * the operator (is / is not, or the range comparator); everything here is just
 * "what are you matching against".
 *
 * `focusRef` is attached to whichever control accepts a NEW value, so the row's
 * "＋" button can hand keyboard focus straight to it.
 */
export interface ValueEditorProps {
  qualifier: SearchQualifier;
  value: QualifierValue;
  /** Unique per row; every control derives its id (and its <label>) from it. */
  id: string;
  focusRef?: RefObject<HTMLInputElement | HTMLSelectElement | null>;
  onChange: (patch: Partial<QualifierValue>) => void;
}

export default function QualifierValueEditor(props: ValueEditorProps) {
  const { qualifier } = props;

  switch (qualifier.kind) {
    case "BOOLEAN":
    case "FLAG":
      // Nothing to type: the operator ("is" / "is not") is the whole value.
      return null;

    case "ENUM":
      return qualifier.repeatable ? <EnumChips {...props} /> : <EnumSelect {...props} />;

    case "NUMBER_RANGE":
    case "DATE_RANGE":
      return <RangeValue {...props} />;

    case "MULTI_TEXT":
      return <TextChips {...props} />;

    case "TEXT":
    default:
      return <SingleText {...props} />;
  }
}

function SingleText({ qualifier, value, id, focusRef, onChange }: ValueEditorProps) {
  return (
    <>
      <label className="sr-only" htmlFor={id}>
        {qualifier.label} value
      </label>
      <input
        id={id}
        ref={focusRef as RefObject<HTMLInputElement | null> | undefined}
        className="fb-input"
        type="text"
        placeholder={qualifier.placeholder ?? "value"}
        value={value.text}
        onChange={(e) => onChange({ text: e.target.value })}
      />
    </>
  );
}

function EnumSelect({ qualifier, value, id, focusRef, onChange }: ValueEditorProps) {
  return (
    <>
      <label className="sr-only" htmlFor={id}>
        {qualifier.label} value
      </label>
      <select
        id={id}
        ref={focusRef as RefObject<HTMLSelectElement | null> | undefined}
        className="fb-select"
        value={value.text}
        onChange={(e) => onChange({ text: e.target.value })}
      >
        <option value="">Choose…</option>
        {(qualifier.allowedValues ?? []).map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </>
  );
}

/** Repeatable ENUM: pick from the fixed list, each pick becomes a chip. */
function EnumChips({ qualifier, value, id, focusRef, onChange }: ValueEditorProps) {
  const remaining = (qualifier.allowedValues ?? []).filter((v) => !value.list.includes(v));

  return (
    <div className="fb-values">
      <ValueChips qualifier={qualifier} value={value} onChange={onChange} />
      {remaining.length > 0 && (
        <>
          <label className="sr-only" htmlFor={id}>
            Add a value to {qualifier.label}
          </label>
          <select
            id={id}
            ref={focusRef as RefObject<HTMLSelectElement | null> | undefined}
            className="fb-select fb-select-add"
            value=""
            onChange={(e) => {
              if (e.target.value) onChange({ list: [...value.list, e.target.value] });
            }}
          >
            <option value="">{value.list.length > 0 ? "add…" : "Choose…"}</option>
            {remaining.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </>
      )}
    </div>
  );
}

/** MULTI_TEXT: free-text chips. Enter or a comma commits, Backspace deletes. */
function TextChips({ qualifier, value, id, focusRef, onChange }: ValueEditorProps) {
  const [draft, setDraft] = useState("");

  function commit(raw: string) {
    const parts = raw
      .split(",")
      .map((p) => p.trim())
      .filter(Boolean)
      .filter((p) => !value.list.includes(p));
    if (parts.length > 0) onChange({ list: [...value.list, ...parts] });
    setDraft("");
  }

  return (
    <div className="fb-values">
      <ValueChips qualifier={qualifier} value={value} onChange={onChange} />
      <label className="sr-only" htmlFor={id}>
        Add a value to {qualifier.label}
      </label>
      <input
        id={id}
        ref={focusRef as RefObject<HTMLInputElement | null> | undefined}
        className="fb-input fb-input-chip"
        type="text"
        placeholder={
          value.list.length > 0 ? "add another…" : (qualifier.placeholder ?? "type, then Enter")
        }
        value={draft}
        onChange={(e) => {
          const next = e.target.value;
          if (next.includes(",")) commit(next);
          else setDraft(next);
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            commit(draft);
          } else if (e.key === "Backspace" && draft === "" && value.list.length > 0) {
            onChange({ list: value.list.slice(0, -1) });
          }
        }}
        onBlur={() => commit(draft)}
      />
    </div>
  );
}

function ValueChips({
  qualifier,
  value,
  onChange,
}: Pick<ValueEditorProps, "qualifier" | "value" | "onChange">) {
  if (value.list.length === 0) return null;
  const joinWord = value.join === "and" ? "AND" : "OR";

  return (
    <ul className="fb-chips">
      {value.list.map((item, index) => (
        <li key={item}>
          {/* The operator is shown BETWEEN the chips so the semantics are
              readable as a sentence, not hidden in a toggle somewhere else. */}
          {index > 0 && <span className="fb-join-word">{joinWord}</span>}
          <span className="fb-chip">
            <span className="fb-chip-text">{item}</span>
            <button
              type="button"
              className="fb-chip-remove"
              aria-label={`Remove ${item} from ${qualifier.label}`}
              onClick={() => onChange({ list: value.list.filter((v) => v !== item) })}
            >
              ×
            </button>
          </span>
        </li>
      ))}
    </ul>
  );
}

/**
 * Range value. The comparator lives in the row's operator select, so this only
 * renders the operand(s): one box, two boxes for "between", or a raw box for
 * anything GitHub accepts that the pair can't express (`*..10`, `2024-01-01..*`).
 */
function RangeValue({ qualifier, value, id, focusRef, onChange }: ValueEditorProps) {
  const isDate = qualifier.kind === "DATE_RANGE";
  const inputType = isDate ? "date" : "number";
  const inputRef = focusRef as RefObject<HTMLInputElement | null> | undefined;

  if (value.comparator === "within") {
    const selected = value.from || "7";
    return (
      <>
        <label className="sr-only" htmlFor={id}>
          {qualifier.label} within the last
        </label>
        <select
          id={id}
          ref={focusRef as RefObject<HTMLSelectElement | null> | undefined}
          className="fb-select"
          value={selected}
          onChange={(e) => onChange({ from: e.target.value })}
        >
          {RELATIVE_WINDOWS.map((w) => (
            <option key={w.value} value={w.value}>
              {w.label}
            </option>
          ))}
        </select>
      </>
    );
  }

  if (value.comparator === "raw") {
    return (
      <>
        <label className="sr-only" htmlFor={id}>
          {qualifier.label} raw GitHub range syntax
        </label>
        <input
          id={id}
          ref={inputRef}
          className="fb-input mono"
          type="text"
          placeholder={qualifier.placeholder ?? (isDate ? "2024-01-01..*" : "*..10")}
          value={value.text}
          onChange={(e) => onChange({ text: e.target.value })}
        />
      </>
    );
  }

  if (value.comparator === "between") {
    return (
      <span className="fb-range-pair">
        <label className="sr-only" htmlFor={id}>
          {qualifier.label} from
        </label>
        <input
          id={id}
          ref={inputRef}
          className="fb-input"
          type={inputType}
          placeholder="from"
          value={value.from}
          onChange={(e) => onChange({ from: e.target.value })}
        />
        <span className="fb-join-word">and</span>
        <label className="sr-only" htmlFor={`${id}-to`}>
          {qualifier.label} to
        </label>
        <input
          id={`${id}-to`}
          className="fb-input"
          type={inputType}
          placeholder="to"
          value={value.to}
          onChange={(e) => onChange({ to: e.target.value })}
        />
      </span>
    );
  }

  return (
    <>
      <label className="sr-only" htmlFor={id}>
        {qualifier.label} value
      </label>
      <input
        id={id}
        ref={inputRef}
        className="fb-input"
        type={inputType}
        placeholder={qualifier.placeholder ?? (isDate ? "" : "0")}
        value={value.from}
        onChange={(e) => onChange({ from: e.target.value })}
      />
      {qualifier.unit && <span className="fb-unit">{qualifier.unit}</span>}
    </>
  );
}
