import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Relief Planning report (ReliefReportRequest): absence and relief-coverage figures
 * over a date range — the on-screen equivalent of the ITQ daily absence report, with
 * summaries by reason and by relief teacher, a tabulated detail list, and CSV export.
 */
interface SummaryRow { label: string; count: number; }
interface DetailRow {
  date: string; absentName: string; reasonLabel: string; timeText: string;
  className: string; roomName: string; reliefName: string; assignedBy: string; statusLabel: string;
}
interface ReliefReportRequest { from: string; to: string; }
interface ReliefReportResponse { byReason?: SummaryRow[]; byRelief?: SummaryRow[]; details?: DetailRow[]; }

@Component({
  selector: 'app-relief-report',
  imports: [
    FormsModule, TableModule, ButtonModule, DatePickerModule, CardModule,
    MessageModule, ProgressSpinnerModule,
  ],
  template: `
    <div class="page">
      <div class="toolbar">
        <div class="left">
          <div><label>From</label><p-datepicker [(ngModel)]="from" dataType="string" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" /></div>
          <div><label>To</label><p-datepicker [(ngModel)]="to" dataType="string" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" /></div>
          <p-button label="Run report" icon="pi pi-search" (onClick)="run()" [disabled]="!from() || !to()" [loading]="loading()" />
        </div>
        <p-button label="Export CSV" icon="pi pi-download" severity="secondary" [text]="true"
          (onClick)="exportCsv()" [disabled]="!details().length" />
      </div>

      @if (loading()) {
        <div class="center"><p-progressSpinner strokeWidth="4" /></div>
      } @else if (error()) {
        <p-message severity="error" [text]="error()!" />
      } @else if (loaded()) {
        <div class="summaries">
          <p-card header="Absences by reason">
            <p-table [value]="byReason()" styleClass="p-datatable-sm">
              <ng-template pTemplate="body" let-r><tr><td>{{ r.label }}</td><td class="num">{{ r.count }}</td></tr></ng-template>
              <ng-template pTemplate="emptymessage"><tr><td colspan="2">No absences.</td></tr></ng-template>
            </p-table>
          </p-card>
          <p-card header="Relief load by teacher">
            <p-table [value]="byRelief()" styleClass="p-datatable-sm">
              <ng-template pTemplate="body" let-r><tr><td>{{ r.label }}</td><td class="num">{{ r.count }}</td></tr></ng-template>
              <ng-template pTemplate="emptymessage"><tr><td colspan="2">No relief assigned.</td></tr></ng-template>
            </p-table>
          </p-card>
        </div>

        <p-card header="Detail">
          <p-table [value]="details()" styleClass="p-datatable-sm p-datatable-gridlines" [paginator]="details().length > 25" [rows]="25">
            <ng-template pTemplate="header">
              <tr><th>Date</th><th>Absent</th><th>Reason</th><th>Time</th><th>Class</th><th>Venue</th><th>Relief</th><th>Assigner</th><th>Status</th></tr>
            </ng-template>
            <ng-template pTemplate="body" let-d>
              <tr>
                <td>{{ d.date }}</td><td>{{ d.absentName }}</td><td>{{ d.reasonLabel }}</td><td>{{ d.timeText }}</td>
                <td>{{ d.className }}</td><td>{{ d.roomName }}</td><td>{{ d.reliefName }}</td><td>{{ d.assignedBy }}</td><td>{{ d.statusLabel }}</td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage"><tr><td colspan="9">No relief records in this range.</td></tr></ng-template>
          </p-table>
        </p-card>
      } @else {
        <p-message severity="info" text="Choose a date range and run the report." />
      }
    </div>
  `,
  styles: [`
    .page { padding: 1rem; display: flex; flex-direction: column; gap: 1rem; }
    .toolbar { display: flex; justify-content: space-between; align-items: end; gap: 1rem; flex-wrap: wrap; }
    .toolbar .left { display: flex; align-items: end; gap: .75rem; }
    label { display: block; font-size: .8rem; opacity: .8; margin-bottom: .25rem; }
    .summaries { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .num { text-align: right; width: 5rem; }
    .center { display: flex; justify-content: center; padding: 3rem; }
    @media (max-width: 800px) { .summaries { grid-template-columns: 1fr; } }
  `],
})
export class ReliefReport {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly from = signal<string>('');
  protected readonly to = signal<string>('');
  protected readonly loading = signal(false);
  protected readonly loaded = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly byReason = signal<SummaryRow[]>([]);
  protected readonly byRelief = signal<SummaryRow[]>([]);
  protected readonly details = signal<DetailRow[]>([]);

  constructor() {
    this.page.set('Relief Report');
  }

  run(): void {
    if (!this.from() || !this.to()) return;
    this.loading.set(true);
    this.error.set(null);
    const request: ReliefReportRequest = { from: this.from(), to: this.to() };
    this.rpc.execute<ReliefReportResponse>('ReliefReportRequest', request).subscribe({
      next: (r) => {
        this.byReason.set(r.byReason ?? []);
        this.byRelief.set(r.byRelief ?? []);
        this.details.set(r.details ?? []);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: (e: ApiError) => { this.error.set(e.message); this.loading.set(false); },
    });
  }

  exportCsv(): void {
    const header = ['Date', 'Absent', 'Reason', 'Time', 'Class', 'Venue', 'Relief', 'Assigner', 'Status'];
    const rows = this.details().map((d) =>
      [d.date, d.absentName, d.reasonLabel, d.timeText, d.className, d.roomName, d.reliefName, d.assignedBy, d.statusLabel]);
    const esc = (v: string) => `"${(v ?? '').replace(/"/g, '""')}"`;
    const csv = [header, ...rows].map((r) => r.map((c) => esc(String(c ?? ''))).join(',')).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `relief-report_${this.from()}_${this.to()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    this.messages.add({ severity: 'success', summary: 'Exported', detail: `${this.details().length} row(s).` });
  }
}
