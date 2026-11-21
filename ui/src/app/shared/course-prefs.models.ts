// DTOs for the time / room preference state shared by the Scheduling Subpart Edit
// and Class Edit pages (org.unitime.timetable.gwt.shared.CoursePreferencesInterface).
// Hand-written to match the Gson iName->name field mapping. Preference levels are
// PreferenceLevel prolog codes: R, -2, -1, 0, 1, 2, P (N = not available, read-only).

export interface IdLabel {
  id?: number;
  label?: string;
}

/**
 * A time pattern with its preference grid; cells are row-major
 * (prefs[day * times.length + time]). Exact-time / pattern-less preferences
 * have no grid and keep the raw `preference` string.
 */
export interface TimePatternGrid {
  timePatternId?: number | null;
  name?: string;
  exactTime?: boolean;
  inherited?: boolean;
  days?: string[];
  times?: string[];
  prefs?: string[];
  preference?: string | null;
}

export interface PrefRow {
  id?: number | null;
  level?: string;
  /** null = all rooms */
  roomIndex?: number | null;
  inherited?: boolean;
}

export interface PreferencesData {
  allowHardTime?: boolean;
  allowHardRoom?: boolean;
  inheritedFrom?: string | null;
  timePatternOptions?: TimePatternGrid[];
  timePrefs?: TimePatternGrid[];
  roomOptions?: IdLabel[];
  buildingOptions?: IdLabel[];
  roomGroupOptions?: IdLabel[];
  roomFeatureOptions?: IdLabel[];
  /** id -1 = all rooms, 0.. = room index; empty for single-room classes */
  roomIndexOptions?: IdLabel[];
  roomPrefs?: PrefRow[];
  buildingPrefs?: PrefRow[];
  roomGroupPrefs?: PrefRow[];
  roomFeaturePrefs?: PrefRow[];
}

export interface PreferencesUpdate {
  timePrefs: TimePatternGrid[];
  roomPrefs: PrefRow[];
  buildingPrefs: PrefRow[];
  roomGroupPrefs: PrefRow[];
  roomFeaturePrefs: PrefRow[];
}

/** Editable preference levels in legacy order, with the labels used on screen. */
export interface PrefLevelOption {
  prolog: string;
  label: string;
  color: string;
  hard: boolean;
}

export const PREF_LEVEL_OPTIONS: PrefLevelOption[] = [
  { prolog: 'R', label: 'Required', color: '#3c3cb4', hard: true },
  { prolog: '-2', label: 'Strongly Preferred', color: '#0f821e', hard: false },
  { prolog: '-1', label: 'Preferred', color: '#32c814', hard: false },
  { prolog: '0', label: 'Neutral', color: '#f0f0f0', hard: false },
  { prolog: '1', label: 'Discouraged', color: '#dcb414', hard: false },
  { prolog: '2', label: 'Strongly Discouraged', color: '#f06428', hard: false },
  { prolog: 'P', label: 'Prohibited', color: '#c81e14', hard: true },
];

export const NOT_AVAILABLE_COLOR = '#969696';

export function prefLevelOption(prolog: string | null | undefined): PrefLevelOption | undefined {
  return PREF_LEVEL_OPTIONS.find((o) => o.prolog === prolog);
}
