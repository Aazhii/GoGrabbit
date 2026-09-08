import { useState } from "react";
import { COMPARATORS, type Comparator, type QualifierValue } from "../lib/searchFilters";
import type { SearchQualifier } from "../types";

interface QualifierControlProps {
  qualifier: SearchQualifier;
  value: QualifierValue;
  /** Set but not yet searched — rendered visually distinct from applied. */
  dirty: boolean;
  idPrefix: string;
  onChange: (patch: Partial<QualifierValue>) => void;
  onClear: () => void;
}

export default function QualifierControl({
  qualifier,
  value,
  dirty,
  idPrefix,
  onChange,
  onClear,
}: QualifierControlProps) {
  const id = `${idPrefix}-${qualifier.key}`;
  const helpId = qualifier.help ? `${id}-help` : undefined;
  const grouped =
    qualifier.kind === "BOOLEAN" || (qualifier.kind === "ENUM" && qualifier.repeatable);

  const head = (
    <div className="qualifier-head">
      {grouped ? (
        <span className="qualifier-label">{qualifier.label}</span>
      ) : (
        <label className="qualifier-label" htmlFor={id}>
          {qualifier.label}
        </label>
      )}
      {qualifier.unit && <span className="qualifier-unit">{qualifier.unit}</span>}
      {qualifier.negatable && (
        <button
          type="button"
          className={value.negated ? "not-toggle not-toggle-on" : "not-toggle"}
          aria-pressed={value.negated}
          title={`Exclude matches for ${qualifier.label} (sends -${qualifier.githubQualifier}:…)`}
          onClick={() => onChange({ negated: !value.negated })}
        >
          not
        </button>
      )}
    </div>
  );

  const body = (
    <>
      {head}
      <QualifierInput
        qualifier={qualifier}
        value={value}
        id={id}
        helpId={helpId}
        onChange={onChange}
      />
      {qualifier.help && (
        <p className="qualifier-help" id={helpId}>
          {qualifier.help}
        </p>
      )}
      {dirty && (
        <p className="qualifier-dirty-note">
          Not applied yet
          <button type="button" className="link-button" onClick={onClear}>
            clear
          </button>
        </p>
      )}
    </>
  );

  const className = dirty ? "qualifier qualifier-dirty" : "qualifier";

  // A radio/checkbox group needs a fieldset+legend for its own accessible name;
  // everything else is a single control with a plain <label htmlFor>.
  return grouped ? (
    <fieldset className={className}>
      <legend className="sr-only">{qualifier.label}</legend>
      {body}
    </fieldset>
  ) : (
    <div className={className}>{body}</div>
  );
}

interface InputProps {
  qualifier: SearchQualifier;
  value: QualifierValue;
  id: string;
  helpId: string | undefined;
  onChange: (patch: Partial<QualifierValue>) => void;
}

function QualifierInput({ qualifier, value, id, helpId, onChange }: InputProps) {
  switch (qualifier.kind) {
    case "ENUM":
      return qualifier.repeatable ? (
        <EnumChecklist qualifier={qualifier} value={value} id={id} onChange={onChange} />
      ) : (
        <select
          id={id}
          aria-describedby={helpId}
          value={value.text}
          onChange={(e) => onChange({ text: e.target.value })}
        >
          <option value="">Any</option>
          {(qualifier.allowedValues ?? []).map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      );

    case "NUMBER_RANGE":
    case "DATE_RANGE":
      return <RangeInput qualifier={qualifier} value={value} id={id} helpId={helpId} onChange={onChange} />;

    case "BOOLEAN":
      return <TriState value={value} id={id} onChange={onChange} />;

    case "FLAG":
      return (
        <span className="flag-row">
          <input
            id={id}
            type="checkbox"
            aria-describedby={helpId}
            checked={value.flag}
            onChange={(e) => onChange({ flag: e.target.checked })}
          />
          <label htmlFor={id} className="flag-label">
            {qualifier.githubQualifier}:{qualifier.flagValue ?? "true"}
          </label>
        </span>
      );

    case "MULTI_TEXT":
      return <ChipInput qualifier={qualifier} value={value} id={id} helpId={helpId} onChange={onChange} />;

    case "TEXT":
    default:
      return (
        <input
          id={id}
          type="text"
          aria-describedby={helpId}
          placeholder={qualifier.placeholder ?? ""}
          value={value.text}
          onChange={(e) => onChange({ text: e.target.value })}
        />
      );
  }
}

/** Multi-select ENUM — checkboxes, because `in:name,description` is an OR set. */
function EnumChecklist({
  qualifier,
  value,
  id,
  onChange,
}: Omit<InputProps, "helpId">) {
  function toggle(option: string) {
    const next = value.list.includes(option)
      ? value.list.filter((v) => v !== option)
      : [...value.list, option];
    onChange({ list: next });
  }

  return (
    <div className="enum-checklist">
      {(qualifier.allowedValues ?? []).map((option) => {
        const optionId = `${id}-${option.replace(/[^a-zA-Z0-9_-]+/g, "-")}`;
        return (
          <span key={option} className="enum-option">
            <input
              id={optionId}
              type="checkbox"
              checked={value.list.includes(option)}
              onChange={() => toggle(option)}
            />
            <label htmlFor={optionId}>{option}</label>
          </span>
        );
      })}
    </div>
  );
}

/**
 * Range control. The comparator + value pair covers the common cases; the "raw
 * syntax" comparator is the escape hatch for anything GitHub accepts that the
 * pair can't express (`*..10`, `2024-01-01..*`, `2024-01-01..2024-06-30`).
 */
function RangeInput({ qualifier, value, id, helpId, onChange }: InputProps) {
  const isDate = qualifier.kind === "DATE_RANGE";
  const inputType = isDate ? "date" : "number";
  const comparator = value.comparator;

  return (
    <div className="range-control">
      <select
        id={id}
        className="range-comparator"
        aria-describedby={helpId}
        value={comparator}
        onChange={(e) => onChange({ comparator: e.target.value as Comparator })}
      >
        {COMPARATORS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>

      {comparator === "raw" ? (
        <>
          <label className="sr-only" htmlFor={`${id}-raw`}>
            {qualifier.label} raw GitHub range syntax
          </label>
          <input
            id={`${id}-raw`}
            className="mono"
            type="text"
            placeholder={qualifier.placeholder ?? (isDate ? "2024-01-01..*" : "*..10")}
            value={value.text}
            onChange={(e) => onChange({ text: e.target.value })}
          />
        </>
      ) : comparator === "between" ? (
        <span className="range-pair">
          <label className="sr-only" htmlFor={`${id}-from`}>
            {qualifier.label} from
          </label>
          <input
            id={`${id}-from`}
            type={inputType}
            placeholder="from"
            value={value.from}
            onChange={(e) => onChange({ from: e.target.value })}
          />
          <span aria-hidden="true">..</span>
          <label className="sr-only" htmlFor={`${id}-to`}>
            {qualifier.label} to
          </label>
          <input
            id={`${id}-to`}
            type={inputType}
            placeholder="to"
            value={value.to}
            onChange={(e) => onChange({ to: e.target.value })}
          />
        </span>
      ) : (
        <>
          <label className="sr-only" htmlFor={`${id}-value`}>
            {qualifier.label} value
          </label>
          <input
            id={`${id}-value`}
            type={inputType}
            placeholder={qualifier.placeholder ?? ""}
            value={value.from}
            onChange={(e) => onChange({ from: e.target.value })}
          />
        </>
      )}
    </div>
  );
}

/** Three states, not a checkbox: "unset" and "false" mean different searches. */
function TriState({
  value,
  id,
  onChange,
}: {
  value: QualifierValue;
  id: string;
  onChange: (patch: Partial<QualifierValue>) => void;
}) {
  const options: { value: QualifierValue["tri"]; label: string }[] = [
    { value: "", label: "Any" },
    { value: "true", label: "Yes" },
    { value: "false", label: "No" },
  ];

  return (
    <div className="tri-state" role="radiogroup">
      {options.map((option) => {
        const optionId = `${id}-${option.value || "any"}`;
        return (
          <span key={option.label} className={value.tri === option.value ? "tri-on" : undefined}>
            <input
              id={optionId}
              type="radio"
              name={id}
              checked={value.tri === option.value}
              onChange={() => onChange({ tri: option.value })}
            />
            <label htmlFor={optionId}>{option.label}</label>
          </span>
        );
      })}
    </div>
  );
}

/** Comma-joined tag input for MULTI_TEXT qualifiers. */
function ChipInput({ qualifier, value, id, helpId, onChange }: InputProps) {
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
    <div className="chip-input">
      {value.list.length > 0 && (
        <ul className="chip-input-chips">
          {value.list.map((item) => (
            <li key={item}>
              <span>{item}</span>
              <button
                type="button"
                aria-label={`Remove ${item} from ${qualifier.label}`}
                onClick={() => onChange({ list: value.list.filter((v) => v !== item) })}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <input
        id={id}
        type="text"
        aria-describedby={helpId}
        placeholder={qualifier.placeholder ?? "type a value, press Enter"}
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
