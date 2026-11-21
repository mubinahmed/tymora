import { Component, ElementRef, OnDestroy, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { ApiError } from '../../core/models';
import { AttendanceConfig, AttendanceResponse, Dashboard, DashboardAlert, ReportRow, TrendPoint } from './attendance.models';

/** Student-day statuses in severity order; colours validated as an adjacent set in both modes (dataviz validator). */
const SEGMENTS: { key: keyof Dashboard; label: string; cls: string }[] = [
  { key: 'present', label: 'Present', cls: 's-present' },
  { key: 'late', label: 'Late', cls: 's-late' },
  { key: 'partial', label: 'Partly absent', cls: 's-partial' },
  { key: 'absent', label: 'Absent', cls: 's-absent' },
  { key: 'excused', label: 'Excused', cls: 's-excused' },
];

interface Segment { label: string; cls: string; count: number; share: number; }

/**
 * Attendance dashboard (B1-7.8.4): one day's student statuses (hero rate, tiles, segmented bar), sessions taken,
 * attendance by a chosen grouping for the day / week / term to date, the daily trend over the term, operational
 * alerts that open the tab or report resolving them, and the classes with the lowest attendance.
 * Backed by AttendanceRequest DASHBOARD.
 */
@Component({
  selector: 'app-attendance-dashboard',
  imports: [DatePipe, DecimalPipe, FormsModule, ButtonModule, SelectModule, SelectButtonModule, InputTextModule, ProgressSpinnerModule, TooltipModule],
  templateUrl: './attendance-dashboard.html',
  styleUrl: './attendance-dashboard.scss',
})
export class AttendanceDashboard implements OnInit, OnDestroy {
  private rpc = inject(RpcService);
  private messages = inject(MessageService);

  readonly config = input<AttendanceConfig | undefined>();
  readonly initialDate = input<string | undefined>();
  readonly termStart = input<string | undefined>();
  readonly termEnd = input<string | undefined>();
  /** tab:<tab> or report:<id> */
  readonly navigate = output<string>();

  protected readonly data = signal<Dashboard | null>(null);
  protected readonly loading = signal(false);
  protected date = '';
  protected groupBy = 'SUBJECT';
  protected period = 'TERM';
  protected readonly groupTable = signal(false);
  protected readonly trendTable = signal(false);

  protected readonly groupOptions = [
    { label: 'School / department', value: 'SCHOOL' },
    { label: 'Subject', value: 'SUBJECT' },
    { label: 'Course / LU', value: 'COURSE' },
    { label: 'Class / group', value: 'CLASS' },
    { label: 'Staff', value: 'INSTRUCTOR' },
    { label: 'Activity type', value: 'ITYPE' },
    { label: 'Weekday', value: 'WEEKDAY' },
    { label: 'Start time', value: 'TIME' },
    { label: 'Room / facility', value: 'ROOM' },
  ];
  protected readonly periodOptions = [
    { label: 'Day', value: 'DAY' },
    { label: 'Week', value: 'WEEK' },
    { label: 'Term to date', value: 'TERM' },
  ];

  // ---------------------------------------------------------------- derived

  protected readonly segments = computed<Segment[]>(() => {
    const d = this.data();
    if (!d || !d.expected) return [];
    return SEGMENTS.map((s) => ({ label: s.label, cls: s.cls, count: d[s.key] as number, share: (100 * (d[s.key] as number)) / d.expected }));
  });

  protected readonly notRecordedShare = computed(() => {
    const d = this.data();
    return d && d.expected ? (100 * d.notRecorded) / d.expected : 0;
  });

  protected readonly groupLabel = computed(() => this.groupOptions.find((o) => o.value === this.data()?.groupBy)?.label ?? '');

  // ---------------------------------------------------------------- trend chart (SVG, sized to its container)

  private readonly trendBox = viewChild<ElementRef<HTMLDivElement>>('trendBox');
  private resize?: ResizeObserver;
  protected readonly width = signal(720);
  protected readonly height = 220;
  protected readonly pad = { l: 40, r: 16, t: 14, b: 28 };
  protected readonly hover = signal<{ i: number; x: number; y: number } | null>(null);

  protected readonly yDomain = computed<[number, number]>(() => {
    const t = this.data()?.trend ?? [];
    const c = this.config();
    const lows = t.map((p) => p.ratePct).concat(c?.eligibilityPct != null ? [c.eligibilityPct] : [], c ? [c.minPct] : []);
    const lo = lows.length ? Math.min(...lows) : 0;
    return [Math.max(0, Math.floor((lo - 5) / 5) * 5), 100];
  });

  protected readonly yTicks = computed(() => {
    const [lo, hi] = this.yDomain();
    const step = hi - lo > 40 ? 20 : hi - lo > 20 ? 10 : 5;
    const ticks: number[] = [];
    for (let v = lo; v <= hi; v += step) ticks.push(v);
    return ticks;
  });

  protected x(i: number): number {
    const n = this.data()?.trend.length ?? 0;
    const w = this.width() - this.pad.l - this.pad.r;
    return this.pad.l + (n <= 1 ? w / 2 : (i * w) / (n - 1));
  }

  protected y(v: number): number {
    const [lo, hi] = this.yDomain();
    const h = this.height - this.pad.t - this.pad.b;
    return this.pad.t + h - ((v - lo) / (hi - lo)) * h;
  }

  protected readonly trendPath = computed(() => {
    const t = this.data()?.trend ?? [];
    this.width();
    return t.map((p, i) => `${i ? 'L' : 'M'}${this.x(i).toFixed(1)},${this.y(p.ratePct).toFixed(1)}`).join(' ');
  });

  /** First teaching day of each month, for the x-axis labels. */
  protected readonly monthTicks = computed(() => {
    const t = this.data()?.trend ?? [];
    this.width();
    const ticks: { x: number; label: string }[] = [];
    let last = '';
    t.forEach((p, i) => {
      const m = p.date.substring(0, 7);
      if (m !== last) {
        last = m;
        ticks.push({ x: this.x(i), label: new Date(p.date + 'T00:00:00').toLocaleDateString('en-GB', { month: 'short' }) });
      }
    });
    return ticks;
  });

  protected onTrendMove(ev: PointerEvent): void {
    const t = this.data()?.trend ?? [];
    if (!t.length) return;
    const svg = ev.currentTarget as SVGElement;
    const rect = svg.getBoundingClientRect();
    const px = ((ev.clientX - rect.left) / rect.width) * this.width();
    const w = this.width() - this.pad.l - this.pad.r;
    const i = Math.max(0, Math.min(t.length - 1, Math.round(((px - this.pad.l) / w) * (t.length - 1))));
    this.hover.set({ i, x: this.x(i), y: this.y(t[i].ratePct) });
  }

  protected hoverPoint(): TrendPoint | null {
    const h = this.hover();
    return h ? (this.data()?.trend[h.i] ?? null) : null;
  }

  /** Keeps the readout off the line: below it when the line is in the upper half, otherwise at the top. */
  protected tooltipTop(): number {
    const h = this.hover();
    if (!h) return 0;
    return h.y < this.height / 2 ? h.y + 14 : 6;
  }

  protected tooltipLeft(): number {
    const h = this.hover();
    if (!h) return 0;
    return Math.min(Math.max(0, h.x - 70), this.width() - 150);
  }

  // ---------------------------------------------------------------- lifecycle and data

  ngOnInit(): void {
    this.date = this.initialDate() ?? '';
    this.load();
  }

  ngOnDestroy(): void {
    this.resize?.disconnect();
  }

  private observe(): void {
    const el = this.trendBox()?.nativeElement;
    if (!el || this.resize) return;
    this.resize = new ResizeObserver((e) => this.width.set(Math.max(320, Math.floor(e[0].contentRect.width))));
    this.resize.observe(el);
  }

  protected load(): void {
    this.loading.set(true);
    this.rpc.execute<AttendanceResponse>('AttendanceRequest', { operation: 'DASHBOARD', date: this.date, period: this.period, dimension: this.groupBy }).subscribe({
      next: (r) => {
        this.loading.set(false);
        if (r.dashboard) {
          this.data.set(r.dashboard);
          this.date = r.dashboard.date;
          setTimeout(() => this.observe());
        }
      },
      error: (e: ApiError) => {
        this.loading.set(false);
        this.messages.add({ severity: 'error', summary: 'Dashboard', detail: e.message, life: 9000 });
      },
    });
  }

  protected shiftDay(days: number): void {
    const d = new Date(this.date + 'T00:00:00');
    d.setDate(d.getDate() + days);
    this.date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    this.load();
  }

  // ---------------------------------------------------------------- helpers for the template

  protected below(r?: number): 'risk' | 'below' | '' {
    const c = this.config();
    if (r == null || !c) return '';
    if (c.eligibilityPct != null && r < c.eligibilityPct) return 'risk';
    return r < c.minPct ? 'below' : '';
  }

  protected groupHover = signal<ReportRow | null>(null);

  protected severityIcon(a: DashboardAlert): string {
    return a.severity === 'critical' ? 'pi pi-exclamation-circle' : a.severity === 'warning' ? 'pi pi-exclamation-triangle' : a.severity === 'ok' ? 'pi pi-check-circle' : 'pi pi-info-circle';
  }

  protected severityLabel(a: DashboardAlert): string {
    return a.severity === 'critical' ? 'Action needed' : a.severity === 'warning' ? 'Attention' : a.severity === 'ok' ? 'Clear' : 'For information';
  }

  protected open(a: DashboardAlert): void {
    this.navigate.emit(a.target);
  }

  protected weekday(d?: string): string {
    return d ? new Date(d + 'T00:00:00').toLocaleDateString('en-GB', { weekday: 'long' }) : '';
  }
}
