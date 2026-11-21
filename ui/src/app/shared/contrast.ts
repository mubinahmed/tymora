/**
 * Readable text colour for a background colour: black or white, whichever has the higher WCAG contrast.
 * Use it wherever a background comes from data (server-sent colours, preference levels) so the text never
 * inherits the theme's text colour, which is light in dark mode and unreadable on pale backgrounds.
 *
 * Accepts rgb()/rgba() and #rgb/#rrggbb. Returns `fallback` for anything it can't parse (e.g. a CSS variable).
 */
export function contrastText(background: string | null | undefined, fallback = 'inherit'): string {
  const rgb = parseColor(background);
  if (!rgb) return fallback;
  const lin = (v: number) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  const l = 0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2]);
  // contrast with white = 1.05 / (l + 0.05); with black = (l + 0.05) / 0.05 — pick the larger
  return 1.05 / (l + 0.05) >= (l + 0.05) / 0.05 ? '#fff' : '#000';
}

export function parseColor(color: string | null | undefined): [number, number, number] | null {
  if (!color) return null;
  const c = color.trim();
  const m = c.match(/^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i);
  if (m) return [Number(m[1]), Number(m[2]), Number(m[3])];
  const h = c.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
  if (!h) return null;
  const hex = h[1].length === 3 ? h[1].split('').map((x) => x + x).join('') : h[1];
  return [parseInt(hex.slice(0, 2), 16), parseInt(hex.slice(2, 4), 16), parseInt(hex.slice(4, 6), 16)];
}

/** Card backgrounds of the two themes (--ut-card in styles.scss), which text in dialogs and tables sits on. */
const LIGHT_BG: [number, number, number] = [255, 255, 255];
const DARK_BG: [number, number, number] = [26, 36, 49]; // #1a2431

function luminance(rgb: [number, number, number]): number {
  const lin = (v: number) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2]);
}

function contrast(a: [number, number, number], b: [number, number, number]): number {
  const la = luminance(a), lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/**
 * A data colour used as TEXT (preference colours, server-coloured table cells), adjusted so it stays readable on
 * the current theme: the hue is kept, and the colour is mixed toward black (light mode) or white (dark mode) until
 * it reaches 4.5:1 contrast with the card background (WCAG AA for normal text). Empty or unparseable values
 * (e.g. '' for neutral = inherit) are returned unchanged.
 *
 * Pass the theme as an argument (ThemeService.isDark()) so templates re-render when the theme changes.
 */
export function readableTextColor(color: string | null | undefined, dark: boolean): string {
  const rgb = parseColor(color);
  if (!rgb) return color ?? '';
  const bg = dark ? DARK_BG : LIGHT_BG;
  const target = dark ? 255 : 0;
  let c: [number, number, number] = [...rgb];
  for (let i = 0; i < 20 && contrast(c, bg) < 4.5; i++)
    c = c.map((v) => Math.round(v + (target - v) * 0.1)) as [number, number, number];
  return '#' + c.map((v) => v.toString(16).padStart(2, '0')).join('');
}
