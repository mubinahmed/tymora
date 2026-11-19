/**
 * UniTime preference levels (PreferenceLevel / cpsolver Constants) for display:
 * the prolog code ("R", "-2", "-1", "0", "1", "2", "P"), its name and the legacy colour.
 */
export interface PrefLevel {
  prolog: string;
  name: string;
  color: string;
}

const LEVELS: Record<string, PrefLevel> = {
  R: { prolog: 'R', name: 'Required', color: '#3c3cb4' },
  '-2': { prolog: '-2', name: 'Strongly preferred', color: '#0f821e' },
  '-1': { prolog: '-1', name: 'Preferred', color: '#32c814' },
  '0': { prolog: '0', name: 'Neutral', color: '' },
  '1': { prolog: '1', name: 'Discouraged', color: '#c49a0a' },
  '2': { prolog: '2', name: 'Strongly discouraged', color: '#f06428' },
  P: { prolog: 'P', name: 'Prohibited', color: '#c81e14' },
  N: { prolog: 'N', name: 'Not available', color: '#696969' },
};

/** cpsolver Constants.preferenceLevel2preference (Required = -100, Prohibited = 100, strong = +-4). */
export function int2prolog(pref: number | null | undefined): string {
  const p = pref ?? 0;
  if (p >= 50) return 'P';
  if (p >= 4) return '2';
  if (p > 0) return '1';
  if (p <= -50) return 'R';
  if (p <= -4) return '-2';
  if (p < 0) return '-1';
  return '0';
}

export function prefLevel(prolog: string | null | undefined): PrefLevel {
  return LEVELS[prolog ?? '0'] ?? LEVELS['0'];
}

/** Colour for a prolog preference; empty string (inherit) for neutral. */
export function prefColor(prolog: string | null | undefined): string {
  return prefLevel(prolog).color;
}

export function intPrefColor(pref: number | null | undefined): string {
  return prefColor(int2prolog(pref));
}

export function intPrefName(pref: number | null | undefined): string {
  return prefLevel(int2prolog(pref)).name;
}
