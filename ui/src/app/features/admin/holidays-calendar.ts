import { Component, computed, input, output } from '@angular/core';

/** One day of the holidays calendar (mirrors SessionEditInterface.HolidayDay). */
export interface HolidayDay {
  date: string; // ISO yyyy-MM-dd
  value: number; // 0 = none, 1 = holiday, 2 = break (editable)
  overlay?: number; // non-editable hint: 0 none, 1 class date, 2 event date
  boundary?: string | null; // non-editable boundary marker key (or null)
}

interface Cell {
  index: number; // position in the flat days[] (== holidays-string index)
  day: number; // day-of-month
  value: number;
  overlay: number;
  boundary: string | null;
  date: string;
}

interface MonthGrid {
  key: string;
  label: string;
  leading: number; // blank cells before day 1 (0 = Sunday)
  cells: Cell[];
}

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** Non-editable boundary markers (key -> label + border color), mirroring the legacy calendar. */
const BOUNDARIES: Record<string, { label: string; color: string }> = {
  sessionBegin: { label: 'Session start', color: '#660000' },
  sessionEnd: { label: 'Session end', color: '#333399' },
  classesEnd: { label: 'Classes end', color: '#339933' },
  examBegin: { label: 'Examination start', color: '#999933' },
  eventBegin: { label: 'Event start', color: '#d4a017' },
  eventEnd: { label: 'Event end', color: '#dc2626' },
};
const BOUNDARY_ORDER = ['sessionBegin', 'classesEnd', 'examBegin', 'sessionEnd', 'eventBegin', 'eventEnd'];

/**
 * Editable holidays calendar — the Angular port of the legacy session holidays
 * grid. Renders one month grid per month the session spans (the backend sends the
 * days in holidays-string index order). Clicking a day cycles its type
 * none → holiday → break → none; the parent rebuilds the holidays string by
 * joining the values in order.
 *
 * Two NON-EDITABLE overlays mirror the legacy calendar: a corner dot marks class
 * dates / event dates (from the session's event-date mapping), and a colored
 * border marks the session / classes / exam / event boundary dates.
 */
@Component({
  selector: 'app-holidays-calendar',
  imports: [],
  templateUrl: './holidays-calendar.html',
})
export class HolidaysCalendar {
  readonly days = input<HolidayDay[]>([]);
  /** Legend labels [none, holiday, break] and their CSS colors, from the backend. */
  readonly names = input<string[]>(['No Holiday', 'Holiday', 'Break']);
  readonly colors = input<string[]>(['rgb(240,240,240)', 'rgb(200,30,20)', 'rgb(240,50,240)']);
  /** Overlay legend labels [class, event] and their colors, from the backend. */
  readonly overlayNames = input<string[]>(['Class date', 'Event date']);
  readonly overlayColors = input<string[]>(['#cc00cc', '#00cccc']);
  readonly disabled = input<boolean>(false);

  readonly daysChange = output<HolidayDay[]>();

  protected readonly weekdays = WEEKDAYS;

  protected readonly legend = computed(() =>
    this.names().map((label, i) => ({ label, color: this.colorFor(i) })),
  );

  protected readonly overlayLegend = computed(() =>
    this.overlayNames().map((label, i) => ({ label, color: this.overlayColors()[i] ?? '#888' })),
  );

  /** Boundary markers actually present in this session, in a stable order. */
  protected readonly boundaryLegend = computed(() => {
    const present = new Set(this.days().map((d) => d.boundary).filter(Boolean) as string[]);
    return BOUNDARY_ORDER.filter((k) => present.has(k)).map((k) => ({ key: k, ...BOUNDARIES[k] }));
  });

  protected readonly months = computed<MonthGrid[]>(() => {
    const out: MonthGrid[] = [];
    let current: MonthGrid | null = null;
    this.days().forEach((d, index) => {
      const [y, m, day] = d.date.split('-').map(Number);
      const key = `${y}-${m}`;
      if (!current || current.key !== key) {
        const firstWeekday = new Date(Date.UTC(y, m - 1, 1)).getUTCDay();
        current = { key, label: `${MONTHS[m - 1]} ${y}`, leading: firstWeekday, cells: [] };
        out.push(current);
      }
      current.cells.push({
        index,
        day,
        value: d.value,
        overlay: d.overlay ?? 0,
        boundary: d.boundary ?? null,
        date: d.date,
      });
    });
    return out;
  });

  colorFor(value: number): string {
    const c = this.colors();
    return c[value] ?? c[0] ?? 'transparent';
  }

  overlayColorFor(overlay: number): string | null {
    return overlay > 0 ? this.overlayColors()[overlay - 1] ?? null : null;
  }

  boundaryColorFor(key: string | null): string | null {
    return key ? BOUNDARIES[key]?.color ?? null : null;
  }

  /** Tooltip combining the holiday type + any non-editable hints. */
  cellTitle(cell: Cell): string {
    const parts = [this.names()[cell.value] ?? ''];
    if (cell.boundary && BOUNDARIES[cell.boundary]) parts.push(BOUNDARIES[cell.boundary].label);
    if (cell.overlay > 0) parts.push(this.overlayNames()[cell.overlay - 1] ?? '');
    return parts.filter(Boolean).join(' · ');
  }

  /** Cycle a day none → holiday → break → none and emit the updated list. */
  cycle(cell: Cell): void {
    if (this.disabled()) return;
    const next = this.days().map((d, i) => (i === cell.index ? { ...d, value: (d.value + 1) % 3 } : d));
    this.daysChange.emit(next);
  }

  blanks(n: number): number[] {
    return Array.from({ length: n }, (_, i) => i);
  }
}
