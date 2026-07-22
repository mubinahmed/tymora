import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { RoomSharingModel, RoomSharingOption } from '../../core/models';

const DAYS_SHORT = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const DAYS_LONG = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

interface Grid {
  /** Header cells (day names in vertical mode, time ranges in horizontal mode). */
  cols: { key: number; label: string }[];
  /** Row headers (time ranges in vertical mode, day names in horizontal mode). */
  rows: { key: number; label: string }[];
  step: number;
  horizontal: boolean;
}

/**
 * Room sharing / availability matrix — Angular port of the legacy GWT
 * RoomSharingWidget. Renders the department/availability grid (days × time
 * slots) for a RoomSharingModel: pick an option from the legend, then click
 * cells (or the day / time / corner headers) to paint it. Mode selector and
 * horizontal toggle switch the granularity/orientation; the note and per-option
 * preference are edited inline. The model is mutated in place and `changed` is
 * emitted so the host can enable its save flag.
 */
@Component({
  selector: 'app-room-sharing-matrix',
  imports: [FormsModule, CheckboxModule, SelectModule, TextareaModule],
  templateUrl: './room-sharing-matrix.html',
})
export class RoomSharingMatrix {
  readonly model = input.required<RoomSharingModel>();
  readonly editable = input<boolean>(true);
  readonly includeNote = input<boolean>(true);
  readonly changed = output<void>();

  protected readonly modeIdx = signal(0);
  protected readonly horizontal = signal(false);
  protected readonly selectedId = signal<number | null>(null);
  /** Bumped after every mutation so the option-reading getters re-evaluate. */
  private readonly version = signal(0);

  /** Seed mode/orientation/selection from the model once (effects may write signals). */
  private seeded = false;
  constructor() {
    effect(() => {
      const m = this.model();
      if (!m || this.seeded) return;
      this.seeded = true;
      this.modeIdx.set(m.defaultMode ?? 0);
      this.horizontal.set(!!m.defaultHorizontal);
      this.selectedId.set(m.defaultOption ?? m.options?.[0]?.id ?? null);
    });
  }

  protected readonly modeOptions = computed(() =>
    (this.model()?.modes ?? []).map((m, i) => ({ label: m.name ?? `Mode ${i + 1}`, value: i })),
  );
  protected readonly options = computed(() => {
    this.version();
    return this.model()?.options ?? [];
  });
  protected readonly additionalOptions = computed(() => {
    this.version();
    const m = this.model();
    const active = new Set((m?.options ?? []).map((o) => o.id));
    return (m?.otherOptions ?? []).filter((o) => !active.has(o.id));
  });
  protected readonly preferences = computed(() => this.model()?.preferences ?? []);

  protected readonly grid = computed<Grid | null>(() => {
    const m = this.model();
    const mode = m?.modes?.[this.modeIdx()];
    if (!mode) return null;
    const step = mode.step ?? 6;
    const days: { key: number; label: string }[] = [];
    let d = mode.firstDay ?? 0;
    // Iterate firstDay..lastDay inclusive, wrapping through the week.
    while (true) {
      days.push({ key: d, label: DAYS_SHORT[d % 7] });
      if (d === (mode.lastDay ?? 4)) break;
      d = (d + 1) % 7;
    }
    const times: { key: number; label: string }[] = [];
    for (let s = mode.firstSlot ?? 90; s < (mode.lastSlot ?? 210); s += step) {
      times.push({ key: s, label: `${this.slot(s)} - ${this.slot(s + step)}` });
    }
    const horizontal = this.horizontal();
    return {
      step,
      horizontal,
      cols: horizontal ? times : days,
      rows: horizontal ? days : times,
    };
  });

  private slot(slot: number): string {
    const h = Math.floor(slot / 12);
    const m = 5 * (slot % 12);
    return `${h}:${m < 10 ? '0' : ''}${m}`;
  }

  // ---- option / editability lookups (mirror RoomSharingModel) ----
  private optById(id: number | null | undefined): RoomSharingOption | undefined {
    const m = this.model();
    const def = m?.defaultOption ?? null;
    const key = id == null ? def : id;
    if (key == null || !m?.options) return undefined;
    const found = m.options.find((o) => o.id === key);
    if (found) return found;
    return key !== def ? this.optById(def) : undefined;
  }

  protected cellOption(day: number, slot: number): RoomSharingOption | undefined {
    this.version();
    const row = this.model()?.model?.[day];
    const id = row ? row[slot] : undefined;
    return this.optById(id);
  }

  protected cellColor(day: number, slot: number): string {
    return this.cellOption(day, slot)?.color ?? 'transparent';
  }
  protected cellCode(day: number, slot: number): string {
    return this.cellOption(day, slot)?.code ?? '';
  }
  protected cellTitle(day: number, slot: number): string {
    const step = this.grid()?.step ?? 6;
    const o = this.cellOption(day, slot);
    return `${DAYS_LONG[day % 7]} ${this.slot(slot)} - ${this.slot(slot + step)}${o ? ': ' + o.name : ''}`;
  }

  private slotEditable(day: number, slot: number): boolean {
    const m = this.model();
    const row = m?.editable?.[day];
    const ed = row ? row[slot] : undefined;
    return ed == null ? m?.defaultEditable !== false : ed;
  }
  protected editableCell(day: number, slot: number): boolean {
    if (!this.modelEditable()) return false;
    const step = this.grid()?.step ?? 6;
    for (let i = 0; i < step; i++) if (!this.slotEditable(day, slot + i)) return false;
    return true;
  }
  protected readonly modelEditable = computed(() => {
    const m = this.model();
    return this.editable() && !!m && (m.options ?? []).some((o) => o.editable) && m.defaultEditable !== false;
  });

  // ---- painting ----
  protected selectOption(id: number | undefined): void {
    if (id == null) return;
    this.selectedId.set(id);
  }
  private paintCell(day: number, slot: number, step: number): void {
    if (!this.editableCell(day, slot)) return;
    const id = this.selectedId();
    if (id == null) return;
    const m = this.model();
    m.model ??= {};
    for (let i = 0; i < step; i++) {
      (m.model[day] ??= {})[slot + i] = id;
    }
  }
  protected paint(day: number, slot: number): void {
    const g = this.grid();
    if (!g) return;
    this.paintCell(day, slot, g.step);
    this.mutated();
  }
  /** Paint every slot of one day (the day header). */
  protected paintDay(day: number): void {
    const g = this.grid();
    if (!g) return;
    const times = g.horizontal ? g.cols : g.rows;
    for (const t of times) this.paintCell(day, t.key, g.step);
    this.mutated();
  }
  /** Paint one time slot across all days (the time header). */
  protected paintTime(slot: number): void {
    const g = this.grid();
    if (!g) return;
    const days = g.horizontal ? g.rows : g.cols;
    for (const d of days) this.paintCell(d.key, slot, g.step);
    this.mutated();
  }
  /** Paint the whole grid (the corner header). */
  protected paintAll(): void {
    const g = this.grid();
    if (!g) return;
    const days = g.horizontal ? g.rows : g.cols;
    const times = g.horizontal ? g.cols : g.rows;
    for (const d of days) for (const t of times) this.paintCell(d.key, t.key, g.step);
    this.mutated();
  }

  // ---- legend option management ----
  protected removable(o: RoomSharingOption): boolean {
    return this.modelEditable() && (o.deletable ?? o.editable) === true && (o.id ?? -1) >= 0;
  }
  protected addOption(o: RoomSharingOption): void {
    const m = this.model();
    (m.options ??= []).push(o);
    this.selectedId.set(o.id ?? null);
    this.mutated();
  }
  protected removeOption(o: RoomSharingOption): void {
    const m = this.model();
    m.options = (m.options ?? []).filter((x) => x.id !== o.id);
    if (this.selectedId() === o.id) this.selectedId.set(m.defaultOption ?? m.options[0]?.id ?? null);
    this.mutated();
  }
  protected setPreference(o: RoomSharingOption, prefId: number): void {
    o.preferenceId = prefId;
    this.changed.emit();
  }
  protected onNote(value: string): void {
    this.model().note = value;
    this.changed.emit();
  }

  protected setMode(idx: number): void {
    this.modeIdx.set(idx);
  }
  protected toggleHorizontal(v: boolean): void {
    this.horizontal.set(v);
  }

  private mutated(): void {
    this.version.update((v) => v + 1);
    this.changed.emit();
  }
}
