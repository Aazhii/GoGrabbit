/* Pure formatting helpers for the result cards — kept out of the component
   file so React Fast Refresh stays happy. */

const numberFormat = new Intl.NumberFormat();

/** Thousands-separated count. Missing values render as "—" via `fallback`. */
export function formatCount(n: number | null | undefined, fallback = "—"): string {
  if (n === null || n === undefined || Number.isNaN(n)) return fallback;
  return numberFormat.format(n);
}

/** GitHub reports repo size in KB; show something a human can read. */
export function formatSizeKb(sizeKb: number | null | undefined): string | null {
  if (sizeKb === null || sizeKb === undefined || Number.isNaN(sizeKb)) return null;
  if (sizeKb < 1024) return `${formatCount(Math.round(sizeKb))} KB`;
  const mb = sizeKb / 1024;
  if (mb < 1024) return `${mb.toFixed(mb < 10 ? 1 : 0)} MB`;
  return `${(mb / 1024).toFixed(1)} GB`;
}

/** Byte counts for code search hits. */
export function formatBytes(bytes: number | null | undefined): string | null {
  if (bytes === null || bytes === undefined || Number.isNaN(bytes)) return null;
  if (bytes < 1024) return `${formatCount(bytes)} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(kb < 10 ? 1 : 0)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}

/**
 * Deterministic hue per language name — a colour dot that stays stable across
 * renders without shipping a 400-entry language colour table (or a dependency).
 * A handful of the most recognisable languages are pinned to their real colour.
 */
const PINNED_LANGUAGE_COLORS: Record<string, string> = {
  javascript: "#f1e05a",
  typescript: "#3178c6",
  python: "#3572a5",
  java: "#b07219",
  go: "#00add8",
  rust: "#dea584",
  ruby: "#701516",
  c: "#555555",
  "c++": "#f34b7d",
  "c#": "#178600",
  php: "#4f5d95",
  swift: "#f05138",
  kotlin: "#a97bff",
  dart: "#00b4ab",
  shell: "#89e051",
  html: "#e34c26",
  css: "#563d7c",
  scala: "#c22d40",
  elixir: "#6e4a7e",
  haskell: "#5e5086",
  lua: "#000080",
  perl: "#0298c3",
  r: "#198ce7",
  vue: "#41b883",
  zig: "#ec915c",
};

export function languageColor(language: string): string {
  const pinned = PINNED_LANGUAGE_COLORS[language.toLowerCase()];
  if (pinned) return pinned;
  let hash = 0;
  for (let i = 0; i < language.length; i += 1) {
    hash = (hash * 31 + language.charCodeAt(i)) % 360;
  }
  return `hsl(${hash} 62% 48%)`;
}
