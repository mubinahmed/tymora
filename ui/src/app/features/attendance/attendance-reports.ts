import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';
import { AttendanceConfig, AttendanceResponse, ReportInfo, ReportTable } from './attendance.models';
import { DateInput } from '../../shared/date-input';

type Row = Record<string, string | number>;

/**
 * Attendance reports (B1-7.8.3, B1-7.8.4, B1-7.8.6): the named reports from AttendanceReports (daily student
 * attendance, period-wise, course and class, subject-wise, staff submission compliance, lateness, consecutive
 * absences, monthly and term, students below the threshold, notification delivery, free grouping), each with
 * its parameters, a sortable table, summary figures, CSV and print. Backed by AttendanceRequest RUN_REPORT.
 */
@Component({
  selector: 'app-attendance-reports',
  imports: [DateInput, DecimalPipe, FormsModule, ButtonModule, SelectModule, InputTextModule, TableModule, TagModule, ProgressSpinnerModule],
  template: `
    <div class="rp">
      <nav class="rp-list" aria-label="Reports">
        @for (r of reports(); track r.id) {
          <button type="button" [class.on]="r.id === selected()?.id" [attr.aria-pressed]="r.id === selected()?.id" [title]="r.description" (click)="select(r)">{{ r.title }}</button>
        }
      </nav>
      <div class="rp-main">
        @if (selected(); as r) {
          <div class="rp-bar">
            @if (r.params.includes('DATE')) { <label><span>Day</span><app-date-input [(ngModel)]="date" /></label> }
            @if (r.params.includes('RANGE')) {
              <label><span>From</span><app-date-input [(ngModel)]="fromDate" /></label>
              <label><span>To</span><app-date-input [(ngModel)]="toDate" /></label>
            }
            @if (r.params.includes('DIMENSION')) { <label><span>Group by</span><p-select [options]="dimensionOptions" [(ngModel)]="dimension" optionLabel="label" optionValue="value" /></label> }
            <p-button label="Run" icon="pi pi-play" size="small" (onClick)="run()" [loading]="loading()" />
            <span class="spacer"></span>
            @if (table()) {
              <p-button label="CSV" icon="pi pi-download" size="small" severity="secondary" [text]="true" (onClick)="csv()" />
              <p-button label="Print" icon="pi pi-print" size="small" severity="secondary" [text]="true" (onClick)="print()" />
            }
          </div>
          @if (table(); as t) {
            <div class="rp-title"><h3>{{ t.title }}</h3><div class="muted">{{ t.description }}</div></div>
            @if (t.summary.length) {
              <div class="rp-summary">@for (s of t.summary; track s[0]) { <div><span>{{ s[0] }}</span><b>{{ s[1] }}</b></div> }</div>
            }
            <p-table [value]="rows()" styleClass="p-datatable-sm p-datatable-striped" [scrollable]="true" [paginator]="rows().length > 50" [rows]="50" [rowsPerPageOptions]="[50, 100, 250]">
              <ng-template pTemplate="header">
                <tr>@for (c of t.columns; track $index) {
                  <th [pSortableColumn]="'c' + $index" [class.r]="numeric(c.type)" [style.min-width]="c.type === 'pct' || c.type === 'share' ? '9rem' : c.type === 'long' ? '16rem' : null">{{ c.name }} <p-sortIcon [field]="'c' + $index" /></th>
                }</tr>
              </ng-template>
              <ng-template pTemplate="body" let-row>
                <tr>@for (c of t.columns; track $index) {
                  @switch (c.type) {
                    @case ('pct') { <td><div class="bar"><div [class]="'fill ' + band(row['c' + $index])" [style.width.%]="row['c' + $index]"></div><span>{{ row['c' + $index] | number: '1.1-1' }}%</span></div></td> }
                    @case ('share') { <td><div class="bar"><div class="fill share" [style.width.%]="row['c' + $index]"></div><span>{{ row['c' + $index] | number: '1.1-1' }}%</span></div></td> }
                    @case ('status') { <td>@if (row['c' + $index]) { <p-tag [value]="row['c' + $index]" [severity]="tone(row['c' + $index])" /> }</td> }
                    @case ('num') { <td class="r">{{ row['c' + $index] }}</td> }
                    @case ('long') { <td class="long" [title]="row['c' + $index]">{{ row['c' + $index] }}</td> }
                    @default { <td class="wrap">{{ row['c' + $index] }}</td> }
                  }
                }</tr>
              </ng-template>
              <ng-template pTemplate="emptymessage"><tr><td [attr.colspan]="t.columns.length">Nothing to report for these parameters.</td></tr></ng-template>
            </p-table>
            @if (t.note) { <div class="muted small rp-note">{{ t.note }}</div> }
            @if (hasPct()) { <div class="muted small rp-note">Bars: blue at or above the minimum ({{ config()?.minPct }}%), amber below it{{ config()?.eligibilityPct != null ? ', red below examination eligibility (' + config()?.eligibilityPct + '%)' : '' }}.</div> }
          } @else if (loading()) {
            <div class="center"><p-progressSpinner strokeWidth="4" /></div>
          }
        }
      </div>
    </div>
  `,
  styles: `
    .rp { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.9rem; }
    .rp-list { display: flex; flex-wrap: wrap; gap: 0.4rem;
      button { all: unset; box-sizing: border-box; cursor: pointer; padding: 0.35rem 0.75rem; border-radius: 999px; border: 1px solid var(--ut-border-strong);
        font-size: 0.82rem; font-weight: 500; color: var(--ut-text); background: var(--ut-card); }
      button:hover, button:focus-visible { background: var(--ut-surface); }
      button.on { background: var(--ut-blue-pale); border-color: var(--ut-blue-soft); color: var(--ut-ink); font-weight: 600; } }
    :host-context(html.dark) .rp-list button.on { background: rgba(57, 135, 229, 0.15); }
    .rp-main { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.8rem; }
    :host ::ng-deep .p-tag { white-space: nowrap; }
    .rp-bar { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.6rem 0.9rem;
      label { display: grid; gap: 0.25rem; font-size: 0.75rem; font-weight: 600; color: var(--ut-muted); } .spacer { flex: 1; } }
    .rp-title h3 { margin: 0 0 0.15rem; font-size: 1rem; color: var(--ut-ink); }
    .rp-summary { display: flex; flex-wrap: wrap; gap: 0.6rem;
      div { background: var(--ut-surface); border-radius: 8px; padding: 0.45rem 0.8rem; display: grid; gap: 0.1rem; min-width: 7rem; }
      span { font-size: 0.72rem; color: var(--ut-muted); } b { font-size: 1.15rem; color: var(--ut-ink); font-weight: 600; } }
    .muted { color: var(--ut-muted); font-size: 0.82rem; } .small { font-size: 0.75rem; } .rp-note { margin-top: -0.3rem; }
    .r { text-align: right; } td.r { font-variant-numeric: tabular-nums; } td.wrap { white-space: normal; min-width: 7rem; }
    td.long { max-width: 22rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    :host ::ng-deep th { white-space: nowrap; } td.r { white-space: nowrap; }
    .bar { position: relative; height: 1.2rem; background: var(--ut-surface); border-radius: 4px; overflow: hidden;
      .fill { height: 100%; border-radius: 0 4px 4px 0; background: #2a78d6; opacity: 0.55; }
      .fill.below { background: #eda100; opacity: 0.7; } .fill.risk { background: #e34948; opacity: 0.6; } .fill.share { background: #888780; opacity: 0.45; }
      span { position: absolute; left: 0.4rem; top: 0; font-size: 0.75rem; line-height: 1.2rem; font-weight: 600; font-variant-numeric: tabular-nums; color: var(--ut-ink); } }
  `,
})
export class AttendanceReports implements OnInit {
  private rpc = inject(RpcService);
  private messages = inject(MessageService);

  readonly reports = input<ReportInfo[]>([]);
  readonly config = input<AttendanceConfig | undefined>();
  readonly termStart = input<string | undefined>();
  readonly termEnd = input<string | undefined>();
  readonly lastRecorded = input<string | undefined>();
  readonly initialReport = input<string | undefined>();

  protected readonly selected = signal<ReportInfo | null>(null);
  protected readonly table = signal<ReportTable | null>(null);
  protected readonly loading = signal(false);
  protected date = '';
  protected fromDate = '';
  protected toDate = '';
  protected dimension = 'CLASS';

  protected readonly dimensionOptions = [
    { label: 'Class / group', value: 'CLASS' }, { label: 'Course / LU', value: 'COURSE' }, { label: 'Subject', value: 'SUBJECT' },
    { label: 'School / department', value: 'SCHOOL' }, { label: 'Student', value: 'STUDENT' }, { label: 'Staff', value: 'INSTRUCTOR' },
    { label: 'Activity type', value: 'ITYPE' }, { label: 'Room / facility', value: 'ROOM' }, { label: 'Date', value: 'DATE' },
    { label: 'Week', value: 'WEEK' }, { label: 'Weekday', value: 'WEEKDAY' }, { label: 'Month', value: 'MONTH' }, { label: 'Start time', value: 'TIME' },
  ];

  protected readonly rows = computed<Row[]>(() => {
    const t = this.table();
    if (!t) return [];
    return t.rows.map((r) => {
      const o: Row = {};
      t.columns.forEach((c, i) => {
        const v = r[i] ?? '';
        o['c' + i] = this.numeric(c.type) && v !== '' && !isNaN(Number(v)) ? Number(v) : v;
      });
      return o;
    });
  });

  protected readonly hasPct = computed(() => !!this.table()?.columns.some((c) => c.type === 'pct'));

  ngOnInit(): void {
    this.date = this.lastRecorded() ?? '';
    this.fromDate = this.termStart() ?? '';
    this.toDate = this.lastRecorded() ?? this.termEnd() ?? '';
    const first = this.reports().find((r) => r.id === this.initialReport()) ?? this.reports()[0];
    if (first) this.select(first);
  }

  /** Opens a report from outside (the dashboard's alerts). */
  open(id: string): void {
    const r = this.reports().find((x) => x.id === id);
    if (r) this.select(r);
  }

  protected select(r: ReportInfo): void {
    this.selected.set(r);
    this.table.set(null);
    this.run();
  }

  protected run(): void {
    const r = this.selected();
    if (!r) return;
    this.loading.set(true);
    this.rpc.execute<AttendanceResponse>('AttendanceRequest', { operation: 'RUN_REPORT', report: r.id, date: this.date, fromDate: this.fromDate, toDate: this.toDate, dimension: this.dimension }).subscribe({
      next: (x) => { this.loading.set(false); this.table.set(x.table ?? null); },
      error: (e: ApiError) => { this.loading.set(false); this.messages.add({ severity: 'error', summary: 'Report', detail: e.message, life: 9000 }); },
    });
  }

  protected numeric(type: string): boolean {
    return type === 'num' || type === 'pct' || type === 'share';
  }

  protected band(v: string | number): string {
    const c = this.config();
    const n = Number(v);
    if (!c || isNaN(n)) return '';
    if (c.eligibilityPct != null && n < c.eligibilityPct) return 'risk';
    return n < c.minPct ? 'below' : '';
  }

  protected tone(v: string | number): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (String(v)) {
      case 'Absent': case 'Failed': case 'Eligibility at risk': return 'danger';
      case 'Partly absent': case 'Late': case 'Not sent': case 'No e-mail address': case 'Below minimum': case 'Open': return 'warn';
      case 'Present': case 'Sent': case 'Closed': return 'success';
      case 'Excused': case 'In progress': return 'info';
      default: return 'secondary';
    }
  }

  private fileName(): string {
    const t = this.table()!;
    const r = this.selected()!;
    const when = r.params.includes('DATE') ? this.date : r.params.includes('RANGE') ? `${this.fromDate} to ${this.toDate}` : '';
    return `${t.title}${when ? ' ' + when : ''}`;
  }

  protected csv(): void {
    const t = this.table();
    if (!t) return;
    downloadCsv(this.fileName(), t.columns.map((c) => c.name), t.rows);
  }

  protected print(): void {
    const t = this.table();
    if (!t) return;
    const esc = (s: string) => s.replace(/[&<>"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[ch]!);
    const num = t.columns.map((c) => this.numeric(c.type));
    const html = `<!doctype html><html><head><meta charset="utf-8"><title>${esc(this.fileName())}</title><style>
      body{font:12px system-ui,sans-serif;margin:16px;color:#111} h1{font-size:16px;margin:0 0 4px} p{color:#555;margin:0 0 10px}
      table{border-collapse:collapse;width:100%} th,td{border-bottom:1px solid #ddd;padding:3px 6px;text-align:left} th{font-size:11px;color:#555}
      td.r,th.r{text-align:right} .sum span{display:inline-block;margin-right:14px}</style></head><body>
      <h1>${esc(t.title)}</h1><p>${esc(this.fileName().substring(t.title.length).trim())} · ${esc(t.description ?? '')}</p>
      <p class="sum">${t.summary.map((s) => `<span>${esc(s[0])}: <b>${esc(s[1])}</b></span>`).join('')}</p>
      <table><thead><tr>${t.columns.map((c, i) => `<th class="${num[i] ? 'r' : ''}">${esc(c.name)}</th>`).join('')}</tr></thead><tbody>
      ${t.rows.map((r) => `<tr>${r.map((v, i) => `<td class="${num[i] ? 'r' : ''}">${esc(v ?? '')}${t.columns[i].type === 'pct' || t.columns[i].type === 'share' ? '%' : ''}</td>`).join('')}</tr>`).join('')}
      </tbody></table>${t.note ? `<p style="margin-top:10px">${esc(t.note)}</p>` : ''}</body></html>`;
    const w = window.open('', '_blank');
    if (!w) return;
    w.document.write(html);
    w.document.close();
    w.focus();
    w.print();
  }
}
