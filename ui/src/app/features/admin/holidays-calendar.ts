import { Component, computed, input, output } from '@angular/core';

/** One day of the holidays calendar (mirrors SessionEditInterface.HolidayDay). */
export interface HolidayDay {
  date: string; // ISO yyyy-MM-dd
  value: number; // 0 = none, 1 = holiday, 2 = break
}

interface Cell {
  index: number; // position in the flat days[] (== holidays-string index)
  day: number; // day-of-month
  value: number;
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

/**
 * Editable holidays calendar — the Angular port of the legacy session holidays
 * grid. Renders one month grid per month the session spans (the backend sends the
 * days in holidays-string index order). Clicking a day cycles its type
 * none → holiday → break → none. Emits the full updated day list; the parent
 * rebuilds the holidays string by joining the values in order.
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
  readonly disabled = input<boolean>(false);

  readonly daysChange = output<HolidayDay[]>();

  protected readonly weekdays = WEEKDAYS;

  protected readonly legend = computed(() =>
    this.names().map((label, i) => ({ label, color: this.colorFor(i) })),
  );

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
      current.cells.push({ index, day, value: d.value, date: d.date });
    });
    return out;
  });

  colorFor(value: number): string {
    const c = this.colors();
    return c[value] ?? c[0] ?? 'transparent';
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
