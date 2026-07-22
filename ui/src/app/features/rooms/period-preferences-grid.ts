import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import { PeriodPreferenceModel, PreferenceInterface } from '../../core/models';

const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/**
 * Examination period-preferences grid — Angular port of the legacy GWT
 * PeriodPreferencesWidget (final-exam / non-calendar layout: exam dates × start
 * times). Pick a preference from the legend, then click cells (or date / time /
 * corner headers) to paint it. The model is mutated in place and `changed` is
 * emitted so the host enables the EXAM_PREFS save flag. The midterm month-grid
 * layout of the legacy widget is not reproduced; the tabular layout is used for
 * both exam types.
 */
@Component({
  selector: 'app-period-preferences-grid',
  imports: [FormsModule, CheckboxModule],
  templateUrl: './period-preferences-grid.html',
})
export class PeriodPreferencesGrid {
  readonly model = input.required<PeriodPreferenceModel>();
  readonly editable = input<boolean>(true);
  readonly changed = output<void>();

  protected readonly horizontal = signal(false);
  protected readonly selectedId = signal<number | null>(null);
  private readonly version = signal(0);
  private seeded = false;

  constructor() {
    effect(() => {
      const m = this.model();
      if (!m || this.seeded) return;
      this.seeded = true;
      this.horizontal.set(!!m.horizontal);
      this.selectedId.set(m.selectedPreference ?? m.preferences?.[0]?.id ?? null);
    });
  }

  protected readonly preferences = computed(() => this.model()?.preferences ?? []);

  protected readonly grid = computed(() => {
    const m = this.model();
    if (!m) return null;
    const days = [...(m.days ?? [])].sort((a, b) => a - b).map((d) => ({ key: d, label: this.dateLabel(d) }));
    const slots = [...(m.starts ?? [])]
      .sort((a, b) => a - b)
      .map((s) => ({ key: s, label: `${this.slot(s)} - ${this.slot(s + this.length(s))}` }));
    const horizontal = this.horizontal();
    return { cols: horizontal ? slots : days, rows: horizontal ? days : slots, horizontal };
  });

  private length(slot: number): number {
    return (this.model()?.periods ?? []).find((p) => p.start === slot)?.length ?? 12;
  }
  private slot(slot: number): string {
    let h = Math.floor(slot / 12);
    if (h > 24) h -= 24;
    const m = 5 * (slot % 12);
    return `${h}:${m < 10 ? '0' : ''}${m}`;
  }
  private dateLabel(day: number): string {
    const raw = this.model()?.firstDate;
    if (!raw) return `Day ${day}`;
    const base = new Date(raw);
    if (isNaN(base.getTime())) return `Day ${day}`;
    const d = new Date(base.getTime());
    d.setDate(d.getDate() + day);
    return `${DOW[d.getDay()]} ${d.getMonth() + 1}/${d.getDate()}`;
  }

  private prefById(id: number | null | undefined): PreferenceInterface | undefined {
    const m = this.model();
    const def = m?.defaultPreference ?? null;
    const key = id == null ? def : id;
    if (key == null || !m?.preferences) return undefined;
    const found = m.preferences.find((p) => p.id === key);
    if (found) return found;
    return key !== def ? this.prefById(def) : undefined;
  }

  protected hasPeriod(day: number, slot: number): boolean {
    return (this.model()?.periods ?? []).some((p) => p.day === day && p.start === slot);
  }
  protected cellPref(day: number, slot: number): PreferenceInterface | undefined {
    this.version();
    if (!this.hasPeriod(day, slot)) return undefined;
    const row = this.model()?.model?.[day];
    return this.prefById(row ? row[slot] : undefined);
  }
  protected cellColor(day: number, slot: number): string {
    return this.cellPref(day, slot)?.color ?? 'transparent';
  }
  protected cellTitle(day: number, slot: number): string {
    const p = this.cellPref(day, slot);
    return `${this.dateLabel(day)} ${this.slot(slot)} - ${this.slot(slot + this.length(slot))}${p ? ': ' + p.name : ''}`;
  }
  protected editableCell(day: number, slot: number): boolean {
    return this.editable() && this.hasPeriod(day, slot);
  }

  protected selectPref(id: number | undefined): void {
    if (id == null) return;
    this.selectedId.set(id);
    this.model().selectedPreference = id;
  }

  private paintCell(day: number, slot: number): void {
    if (!this.editableCell(day, slot)) return;
    const id = this.selectedId();
    if (id == null) return;
    const m = this.model();
    m.model ??= {};
    (m.model[day] ??= {})[slot] = id;
  }
  protected paint(day: number, slot: number): void {
    this.paintCell(day, slot);
    this.mutated();
  }
  protected paintDay(day: number): void {
    for (const s of this.model()?.starts ?? []) this.paintCell(day, s);
    this.mutated();
  }
  protected paintSlot(slot: number): void {
    for (const d of this.model()?.days ?? []) this.paintCell(d, slot);
    this.mutated();
  }
  protected paintAll(): void {
    for (const d of this.model()?.days ?? []) for (const s of this.model()?.starts ?? []) this.paintCell(d, s);
    this.mutated();
  }

  protected toggleHorizontal(v: boolean): void {
    this.horizontal.set(v);
  }

  private mutated(): void {
    this.version.update((v) => v + 1);
    this.changed.emit();
  }
}
