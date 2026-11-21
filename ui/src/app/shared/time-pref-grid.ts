import { Component, computed, effect, input, model, signal } from '@angular/core';
import { contrastText } from './contrast';
import { NOT_AVAILABLE_COLOR, PREF_LEVEL_OPTIONS, PrefLevelOption, prefLevelOption } from './course-prefs.models';

/**
 * Reusable time preference grid (legacy RequiredTimeTable / TimePatternModel):
 * rows are the time pattern's days, columns its start times. Pick a level from the
 * palette, then click a cell, a day row header (whole day), a time column header
 * (whole column) or the corner (everything) to set it. Cells are PreferenceLevel
 * prolog codes, row-major: prefs[day * times.length + time]. "N" (not available)
 * cells are read-only. Two-way bind the cells with [(prefs)].
 */
@Component({
  selector: 'app-time-pref-grid',
  template: `
    <div class="tpg">
      <div class="tpg-palette" role="radiogroup" aria-label="Preference level">
        @for (o of levels(); track o.prolog) {
          <button
            type="button"
            class="tpg-level"
            role="radio"
            [attr.aria-checked]="o.prolog === selected()"
            [class.tpg-level-sel]="o.prolog === selected()"
            [disabled]="disabled()"
            (click)="selected.set(o.prolog)"
          >
            <span class="tpg-swatch" [style.background-color]="o.color"></span>
            {{ o.label }}
          </button>
        }
      </div>

      <div class="tpg-scroll">
        <table class="tpg-grid">
          <thead>
            <tr>
              <th class="tpg-corner" [class.tpg-click]="!disabled()" title="Set all times" (click)="paintAll()">
                Day \\ Time
              </th>
              @for (t of times(); track $index; let ti = $index) {
                <th class="tpg-head" [class.tpg-click]="!disabled()" [title]="'Set all ' + t" (click)="paintTime(ti)">
                  {{ t }}
                </th>
              }
            </tr>
          </thead>
          <tbody>
            @for (d of days(); track $index; let di = $index) {
              <tr>
                <th class="tpg-rowhead" [class.tpg-click]="!disabled()" [title]="'Set all of ' + d" (click)="paintDay(di)">
                  {{ d }}
                </th>
                @for (t of times(); track $index; let ti = $index) {
                  <td
                    class="tpg-cell"
                    [class.tpg-click]="!disabled() && cell(di, ti) !== 'N'"
                    [style.background-color]="color(cell(di, ti))"
                    [style.color]="textColor(cell(di, ti))"
                    [title]="d + ' ' + t + ': ' + label(cell(di, ti))"
                    (click)="paint(di, ti)"
                  >
                    {{ abbv(cell(di, ti)) }}
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [
    `
      .tpg { display: flex; flex-direction: column; gap: 0.6rem; }
      .tpg-palette { display: flex; flex-wrap: wrap; gap: 0.35rem; }
      .tpg-level {
        display: inline-flex; align-items: center; gap: 0.4rem;
        font: inherit; font-size: 0.8rem; color: inherit;
        background: var(--p-content-background, #fff);
        border: 1px solid var(--p-content-border-color, #e5e7eb);
        border-radius: 999px; padding: 0.15rem 0.65rem 0.15rem 0.3rem; cursor: pointer;
      }
      .tpg-level:disabled { cursor: default; opacity: 0.6; }
      .tpg-level-sel { outline: 2px solid var(--p-primary-color, #0b5fff); font-weight: 600; }
      .tpg-swatch { width: 1.1rem; height: 1.1rem; border-radius: 3px; border: 1px solid rgba(0, 0, 0, 0.2); }
      .tpg-scroll { overflow-x: auto; }
      .tpg-grid { border-collapse: collapse; font-size: 0.75rem; }
      .tpg-grid th, .tpg-grid td { border: 1px solid var(--p-content-border-color, #d1d5db); text-align: center; }
      .tpg-corner, .tpg-head, .tpg-rowhead {
        background: var(--p-content-background, #f8fafc); font-weight: 600; padding: 0.2rem 0.45rem; white-space: nowrap;
      }
      .tpg-rowhead { text-align: left; }
      .tpg-cell { min-width: 2.6rem; height: 1.6rem; font-weight: 600; user-select: none; }
      .tpg-click { cursor: pointer; }
      th.tpg-click:hover { background: var(--p-content-hover-background, #eef1f6); }
    `,
  ],
})
export class TimePrefGrid {
  readonly days = input<string[]>([]);
  readonly times = input<string[]>([]);
  readonly prefs = model<string[]>([]);
  readonly allowHard = input<boolean>(true);
  readonly disabled = input<boolean>(false);

  /** Currently selected palette level (prolog). */
  protected readonly selected = signal<string>('R');

  protected readonly levels = computed<PrefLevelOption[]>(() =>
    this.allowHard() ? PREF_LEVEL_OPTIONS : PREF_LEVEL_OPTIONS.filter((o) => !o.hard),
  );

  constructor() {
    effect(() => {
      // keep the selection valid when hard preferences are not allowed
      const lv = this.levels();
      if (!lv.some((o) => o.prolog === this.selected())) this.selected.set(lv[0]?.prolog ?? '0');
    });
  }

  protected cell(d: number, t: number): string {
    return this.prefs()[d * this.times().length + t] ?? '0';
  }

  protected color(p: string): string {
    if (p === 'N') return NOT_AVAILABLE_COLOR;
    return prefLevelOption(p)?.color ?? '#f0f0f0';
  }

  /** Black or white text, whichever reads better on the level colour (white was unreadable on Preferred / Discouraged). */
  protected textColor(p: string): string {
    return contrastText(this.color(p), '#000');
  }

  protected label(p: string): string {
    if (p === 'N') return 'Not Available';
    return prefLevelOption(p)?.label ?? 'Neutral';
  }

  protected abbv(p: string): string {
    return p === '0' ? '' : p === 'N' ? 'N/A' : p;
  }

  private update(fn: (d: number, t: number) => boolean): void {
    if (this.disabled()) return;
    const nd = this.days().length;
    const nt = this.times().length;
    const next = [...this.prefs()];
    while (next.length < nd * nt) next.push('0');
    const level = this.selected();
    for (let d = 0; d < nd; d++)
      for (let t = 0; t < nt; t++) {
        const i = d * nt + t;
        if (fn(d, t) && next[i] !== 'N') next[i] = level;
      }
    this.prefs.set(next);
  }

  protected paint(d: number, t: number): void {
    this.update((dd, tt) => dd === d && tt === t);
  }

  protected paintDay(d: number): void {
    this.update((dd) => dd === d);
  }

  protected paintTime(t: number): void {
    this.update((_dd, tt) => tt === t);
  }

  protected paintAll(): void {
    this.update(() => true);
  }
}
