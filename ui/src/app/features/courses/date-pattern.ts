import { Component, OnInit, inject, input, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { Location } from '@angular/common';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { DispDatePatternRequest, DispDatePatternResponse } from './date-pattern.models';

interface DayCell {
  day: number | null;
  iso?: string;
  active?: boolean;
}
interface MonthView {
  label: string;
  weeks: DayCell[][];
}

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

/**
 * Display Date Pattern (legacy dispDatePattern.action) — read-only calendar of a
 * date pattern's active days, backed by the additive DispDatePatternRequest bean.
 * Reached by date-pattern id from the class / subpart detail screens.
 */
@Component({
  selector: 'app-date-pattern',
  imports: [ButtonModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './date-pattern.html',
})
export class DatePatternDisplay implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private location = inject(Location);

  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<DispDatePatternResponse | null>(null);
  protected readonly months = signal<MonthView[]>([]);
  protected readonly DOW = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

  ngOnInit(): void {
    this.page.set('Date Pattern');
    const dpId = this.id() == null ? NaN : Number(this.id());
    const request: DispDatePatternRequest = Number.isFinite(dpId) ? { datePatternId: dpId } : {};
    this.rpc.execute<DispDatePatternResponse>('DispDatePatternRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        if (d.name) this.page.set('Date Pattern — ' + d.name);
        this.months.set(this.buildMonths(d));
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  close(): void {
    this.location.back();
  }

  private buildMonths(d: DispDatePatternResponse): MonthView[] {
    const start = this.parse(d.startDate);
    const end = this.parse(d.endDate);
    if (!start || !end) return [];
    const active = new Set(d.activeDates ?? []);
    const months: MonthView[] = [];
    let y = start.getFullYear();
    let m = start.getMonth();
    const endY = end.getFullYear();
    const endM = end.getMonth();
    let guard = 0;
    while ((y < endY || (y === endY && m <= endM)) && guard++ < 60) {
      const first = new Date(y, m, 1);
      const daysInMonth = new Date(y, m + 1, 0).getDate();
      const cells: DayCell[] = [];
      for (let i = 0; i < first.getDay(); i++) cells.push({ day: null });
      for (let dd = 1; dd <= daysInMonth; dd++) {
        const iso = `${y}-${this.pad(m + 1)}-${this.pad(dd)}`;
        cells.push({ day: dd, iso, active: active.has(iso) });
      }
      while (cells.length % 7 !== 0) cells.push({ day: null });
      const weeks: DayCell[][] = [];
      for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));
      months.push({ label: `${MONTHS[m]} ${y}`, weeks });
      m++;
      if (m > 11) {
        m = 0;
        y++;
      }
    }
    return months;
  }

  private parse(s?: string): Date | null {
    if (!s) return null;
    const d = new Date(s + 'T00:00:00');
    return isNaN(d.getTime()) ? null : d;
  }

  private pad(n: number): string {
    return String(n).padStart(2, '0');
  }
}
