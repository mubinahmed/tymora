import { Component, computed, effect, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TimePrefGrid } from './time-pref-grid';
import {
  IdLabel,
  PREF_LEVEL_OPTIONS,
  PrefLevelOption,
  PrefRow,
  PreferencesData,
  PreferencesUpdate,
  TimePatternGrid,
  prefLevelOption,
} from './course-prefs.models';

type RowKind = 'room' | 'building' | 'roomGroup' | 'roomFeature';

interface GridState extends TimePatternGrid {
  days: string[];
  times: string[];
  prefs: string[];
}

const SOFT_LEVELS: PrefLevelOption[] = PREF_LEVEL_OPTIONS.filter((o) => !o.hard);

interface RowState {
  id: number | null;
  level: string;
  /** -1 = all rooms */
  roomIndex: number;
  inherited: boolean;
}

interface RowSection {
  kind: RowKind;
  title: string;
  item: string;
  add: string;
  placeholder: string;
  options: IdLabel[];
  rows: RowState[];
}

/**
 * Time pattern + time preference + room / building / room group / room feature
 * preference editor shared by Scheduling Subpart Edit and Class Edit (legacy
 * preferencesEdit2.jspf). Seeded from the edit response's `preferences`; the host
 * calls validate() and toUpdate() when saving. Rows / grids inherited from the
 * parent subpart are shown with an "Inherited" tag (legacy shows the effective
 * preferences too); saving them unchanged keeps them inherited, removing one
 * overrides it (stored as neutral), exactly like the legacy page.
 */
@Component({
  selector: 'app-course-prefs-editor',
  imports: [FormsModule, ButtonModule, SelectModule, TagModule, TimePrefGrid],
  templateUrl: './course-prefs-editor.html',
  styles: [
    `
      :host { display: grid; gap: 1.25rem; }
      .cpe-grid-card { border: 1px solid var(--p-content-border-color, #e5e7eb); border-radius: 8px; padding: 0.75rem 1rem; margin-bottom: 0.9rem; }
      .cpe-grid-head { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 0.6rem; }
      .cpe-grid-head h3 { margin: 0; font-size: 0.9rem; font-weight: 600; }
      .cpe-spacer { flex: 1; }
      .cpe-add { display: flex; align-items: flex-end; gap: 0.75rem; flex-wrap: wrap; }
      .cpe-add .field { flex: 0 1 22rem; }
      .cpe-block { margin-top: 1.1rem; }
      .cpe-block:first-of-type { margin-top: 0; }
      .cpe-block h3 { margin: 0 0 0.5rem; font-size: 0.85rem; font-weight: 600; }
      .cpe-row { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 0.45rem; flex-wrap: wrap; }
      .cpe-item { flex: 1 1 18rem; min-width: 14rem; }
      .cpe-level { flex: 0 0 14rem; }
      .cpe-index { flex: 0 0 9rem; }
      .cpe-swatch { width: 0.9rem; height: 0.9rem; border-radius: 3px; border: 1px solid rgba(0, 0, 0, 0.2); flex: none; }
      .cpe-empty { font-size: 0.85rem; color: var(--p-text-muted-color, #6b7280); margin: 0 0 0.5rem; }
    `,
  ],
})
export class CoursePrefsEditor {
  readonly data = input<PreferencesData | null | undefined>(null);

  protected readonly grids = signal<GridState[]>([]);
  protected readonly selectedPatternId = signal<number | null>(null);
  protected readonly rows = signal<Record<RowKind, RowState[]>>({ room: [], building: [], roomGroup: [], roomFeature: [] });

  private seededFrom: PreferencesData | null = null;

  constructor() {
    effect(() => {
      const d = this.data();
      if (!d || d === this.seededFrom) return;
      this.seededFrom = d;
      this.grids.set((d.timePrefs ?? []).map((g) => ({ ...g, days: g.days ?? [], times: g.times ?? [], prefs: [...(g.prefs ?? [])] })));
      this.rows.set({
        room: this.seedRows(d.roomPrefs),
        building: this.seedRows(d.buildingPrefs),
        roomGroup: this.seedRows(d.roomGroupPrefs),
        roomFeature: this.seedRows(d.roomFeaturePrefs),
      });
      this.selectedPatternId.set(null);
    });
  }

  private seedRows(rows: PrefRow[] | undefined): RowState[] {
    return (rows ?? []).map((r) => ({
      id: r.id ?? null,
      level: r.level ?? '0',
      roomIndex: r.roomIndex == null ? -1 : r.roomIndex,
      inherited: !!r.inherited,
    }));
  }

  protected readonly allowHardTime = computed(() => this.data()?.allowHardTime !== false);
  protected readonly allowHardRoom = computed(() => this.data()?.allowHardRoom !== false);
  protected readonly inheritedFrom = computed(() => this.data()?.inheritedFrom || 'parent');
  protected readonly roomIndexOptions = computed(() => this.data()?.roomIndexOptions ?? []);

  /** Time patterns not yet added. */
  protected readonly availablePatterns = computed(() => {
    const used = new Set(this.grids().map((g) => g.timePatternId));
    return (this.data()?.timePatternOptions ?? []).filter((o) => !used.has(o.timePatternId));
  });

  protected readonly sections = computed<RowSection[]>(() => {
    const d = this.data();
    const r = this.rows();
    return [
      { kind: 'room', title: 'Room Preferences', item: 'Room', add: 'Add Room Preference', placeholder: 'Select room', options: d?.roomOptions ?? [], rows: r.room },
      { kind: 'building', title: 'Building Preferences', item: 'Building', add: 'Add Building Preference', placeholder: 'Select building', options: d?.buildingOptions ?? [], rows: r.building },
      { kind: 'roomGroup', title: 'Room Group Preferences', item: 'Room Group', add: 'Add Room Group Preference', placeholder: 'Select room group', options: d?.roomGroupOptions ?? [], rows: r.roomGroup },
      { kind: 'roomFeature', title: 'Room Feature Preferences', item: 'Room Feature', add: 'Add Room Feature Preference', placeholder: 'Select room feature', options: d?.roomFeatureOptions ?? [], rows: r.roomFeature },
    ];
  });

  protected levelsFor(row: RowState): PrefLevelOption[] {
    // a hard level without the right to use hard preferences is shown, locked (legacy disabled row)
    return this.allowHardRoom() || this.levelLocked(row) ? PREF_LEVEL_OPTIONS : SOFT_LEVELS;
  }

  protected levelLocked(row: RowState): boolean {
    return !this.allowHardRoom() && !!prefLevelOption(row.level)?.hard;
  }

  protected levelColor(prolog: string): string {
    return prefLevelOption(prolog)?.color ?? 'transparent';
  }

  // ---- time patterns ----

  protected addTimePattern(): void {
    const id = this.selectedPatternId();
    if (id == null) return;
    const opt = (this.data()?.timePatternOptions ?? []).find((o) => o.timePatternId === id);
    if (!opt) return;
    const grid: GridState = {
      ...opt,
      inherited: false,
      days: opt.days ?? [],
      times: opt.times ?? [],
      prefs: [...(opt.prefs ?? [])],
    };
    // legacy addTimePattern: exact-time and regular patterns do not mix
    this.grids.update((list) => [...list.filter((g) => !!g.exactTime === !!grid.exactTime), grid]);
    this.selectedPatternId.set(null);
  }

  protected removeTimePattern(index: number): void {
    this.grids.update((list) => list.filter((_g, i) => i !== index));
  }

  protected gridChanged(g: GridState): void {
    g.inherited = false;
  }

  // ---- room / building / group / feature rows ----

  protected addRow(kind: RowKind): void {
    const level = this.allowHardRoom() ? 'R' : '-1';
    this.rows.update((all) => ({ ...all, [kind]: [...all[kind], { id: null, level, roomIndex: -1, inherited: false }] }));
  }

  protected removeRow(kind: RowKind, index: number): void {
    this.rows.update((all) => ({ ...all, [kind]: all[kind].filter((_r, i) => i !== index) }));
  }

  protected rowChanged(row: RowState): void {
    row.inherited = false;
  }

  // ---- host API ----

  /** Legacy PreferencesForm.checkPrefs: no duplicate rows. Returns an error message or null. */
  validate(): string | null {
    for (const s of this.sections()) {
      const seen = new Set<string>();
      for (const r of s.rows) {
        if (r.id == null) continue;
        const key = r.id + ':' + r.roomIndex;
        if (seen.has(key)) {
          const label = s.options.find((o) => o.id === r.id)?.label ?? String(r.id);
          return `Duplicate ${s.item.toLowerCase()} preference: ${label}.`;
        }
        seen.add(key);
      }
    }
    return null;
  }

  toUpdate(): PreferencesUpdate {
    const conv = (rows: RowState[]): PrefRow[] =>
      rows
        .filter((r) => r.id != null)
        .map((r) => ({ id: r.id, level: r.level, roomIndex: r.roomIndex < 0 ? null : r.roomIndex }));
    const r = this.rows();
    return {
      timePrefs: this.grids().map((g) => ({
        timePatternId: g.timePatternId ?? null,
        prefs: g.exactTime ? [] : [...(g.prefs ?? [])],
        preference: g.preference ?? null,
      })),
      roomPrefs: conv(r.room),
      buildingPrefs: conv(r.building),
      roomGroupPrefs: conv(r.roomGroup),
      roomFeaturePrefs: conv(r.roomFeature),
    };
  }
}
