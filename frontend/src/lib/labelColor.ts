const FALLBACK_BG = "#d0d7de";

function normalizeHex(hex: string): string | null {
  const clean = hex.replace(/^#/, "").trim();
  const expanded =
    clean.length === 3
      ? clean
          .split("")
          .map((c) => c + c)
          .join("")
      : clean;
  return /^[0-9a-fA-F]{6}$/.test(expanded) ? expanded : null;
}

function parseHex(hex: string): [number, number, number] | null {
  const clean = hex.replace(/^#/, "").trim();
  const expanded =
    clean.length === 3
      ? clean
          .split("")
          .map((c) => c + c)
          .join("")
      : clean;
  if (!/^[0-9a-fA-F]{6}$/.test(expanded)) return null;
  return [
    parseInt(expanded.slice(0, 2), 16),
    parseInt(expanded.slice(2, 4), 16),
    parseInt(expanded.slice(4, 6), 16),
  ];
}

function channel(v: number): number {
  const s = v / 255;
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
}

/**
 * Background/foreground pair for a GitHub label, picking black or white text
 * from the background's relative luminance (WCAG) so it stays readable.
 */
export function labelColors(hex: string | null | undefined): {
  background: string;
  color: string;
  border: string;
} {
  const normalized = normalizeHex(hex ?? "");
  const rgb = normalized ? parseHex(normalized) : null;
  if (!normalized || !rgb) {
    return { background: FALLBACK_BG, color: "#1f2328", border: "rgba(0, 0, 0, 0.15)" };
  }
  const [r, g, b] = rgb;
  const luminance = 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
  // Contrast against white vs. black; pick whichever wins.
  const contrastWithWhite = 1.05 / (luminance + 0.05);
  const contrastWithBlack = (luminance + 0.05) / 0.05;
  return {
    background: `#${normalized}`,
    color: contrastWithWhite >= contrastWithBlack ? "#ffffff" : "#1f2328",
    border: "rgba(0, 0, 0, 0.15)",
  };
}
