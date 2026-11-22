import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { ClassStat, MyAttendanceResponse } from './attendance.models';
import { DisplayDatePipe } from '../../shared/display-date.pipe';
import { DateInput } from '../../shared/date-input';

/**
 * My Attendance: a student's attendance per class against the configured minimum and examination eligibility
 * thresholds, warnings, the session-by-session record, and submission of medical certificates or leave
 * (with the document) for approval. Backed by MyAttendanceRequest.
 */
@Component({
  selector: 'app-my-attendance',
  imports: [DateInput, DisplayDatePipe, DatePipe, DecimalPipe, FormsModule, ButtonModule, TableModule, TagModule, MessageModule, SelectModule, DialogModule, InputTextModule, TextareaModule, ProgressSpinnerModule],
  template: `
    @if (loading()) {
      <div class="center"><p-progressSpinner strokeWidth="4" /></div>
    } @else if (error()) {
      <p-message severity="error" [text]="error()!" />
    } @else if (data(); as d) {
      <div class="ma-page">
        @if (!d.student) {
          <div class="empty-state">{{ d.message ?? 'Attendance is recorded for students only.' }}</div>
        } @else {
          <div class="ma-head">
            <div>
              <div class="ma-name">{{ d.name }}</div>
              <div class="muted">Minimum attendance {{ d.config?.minPct }}%@if (d.config?.eligibilityPct != null) { · examination eligibility {{ d.config?.eligibilityPct }}% }</div>
            </div>
            <span class="spacer"></span>
            <div class="ma-overall" [class]="rate(d.overallPct)"><span>{{ d.overallPct }}%</span><small>overall</small></div>
            <p-button label="Submit medical certificate / leave" icon="pi pi-file-plus" (onClick)="openSubmit()" />
          </div>
          @for (w of d.warnings ?? []; track w) { <p-message severity="warn" [text]="w" /> }

          <section>
            <h2><i class="pi pi-chart-bar"></i> By class</h2>
            <p-table [value]="d.classes ?? []" styleClass="p-datatable-sm p-datatable-striped">
              <ng-template pTemplate="header"><tr><th>Class</th><th>Lecturer</th><th class="r">Sessions</th><th class="r">Present</th><th class="r">Late</th><th class="r">Absent</th><th class="r">Excused</th><th style="width:26%">Attendance</th></tr></ng-template>
              <ng-template pTemplate="body" let-c>
                <tr>
                  <td><b>{{ c.className }}</b></td><td>{{ c.instructors }}</td><td class="r">{{ c.sessions }}</td><td class="r">{{ c.present }}</td><td class="r">{{ c.late }}</td><td class="r">{{ c.absent }}</td><td class="r">{{ c.excused }}</td>
                  <td><div class="bar"><div [class]="'fill ' + rate(c.ratePct)" [style.width.%]="c.ratePct"></div><span>{{ c.sessions ? c.ratePct + '%' : '—' }}</span></div>
                    @if (standing(c)) { <p-tag [value]="standing(c)" [severity]="c.standing === 'ELIGIBILITY_RISK' ? 'danger' : 'warn'" /> }</td>
                </tr>
              </ng-template>
              <ng-template pTemplate="emptymessage"><tr><td colspan="8">You are not enrolled in any class.</td></tr></ng-template>
            </p-table>
          </section>

          <section>
            <h2><i class="pi pi-file"></i> My medical certificates and leave</h2>
            <p-table [value]="d.excuses ?? []" styleClass="p-datatable-sm p-datatable-striped">
              <ng-template pTemplate="header"><tr><th>Submitted</th><th>Type</th><th>Period</th><th>Note</th><th>Document</th><th>Status</th></tr></ng-template>
              <ng-template pTemplate="body" let-e>
                <tr><td>{{ e.submittedTs | date: 'd MMM, HH:mm' }}</td><td>{{ typeLabel(e.type) }}</td><td>{{ e.fromDate | date: 'd MMM' }} – {{ e.toDate | date: 'd MMM y' }}</td>
                  <td class="wrap">{{ e.note }}</td><td>{{ e.fileName }}</td>
                  <td><p-tag [value]="e.status" [severity]="e.status === 'APPROVED' ? 'success' : e.status === 'REJECTED' ? 'danger' : 'warn'" />
                    @if (e.decisionNote) { <div class="muted small">{{ e.decisionNote }}</div> }</td></tr>
              </ng-template>
              <ng-template pTemplate="emptymessage"><tr><td colspan="6">Nothing submitted.</td></tr></ng-template>
            </p-table>
          </section>

          <section>
            <h2><i class="pi pi-list"></i> Session record</h2>
            <p-table [value]="d.records ?? []" styleClass="p-datatable-sm p-datatable-striped" [paginator]="(d.records?.length ?? 0) > 20" [rows]="20">
              <ng-template pTemplate="header"><tr><th>Date</th><th>Time</th><th>Class</th><th>Room</th><th>Status</th><th>Recorded</th></tr></ng-template>
              <ng-template pTemplate="body" let-r>
                <tr><td>{{ r.date | displayDate }}</td><td>{{ r.time }}</td><td>{{ r.className }}</td><td>{{ r.room }}</td>
                  <td><span [class]="'cnt ' + (r.status ?? '').toLowerCase()">{{ r.status }}</span></td><td class="muted">{{ (r.source ?? '').toLowerCase() }}</td></tr>
              </ng-template>
              <ng-template pTemplate="emptymessage"><tr><td colspan="6">No attendance recorded yet.</td></tr></ng-template>
            </p-table>
          </section>
        }
      </div>

      <p-dialog [(visible)]="submitOpen" header="Submit medical certificate or leave" [modal]="true" [style]="{ width: 'min(560px, 94vw)' }">
        <div class="ma-form">
          <label><span>Type</span><p-select [options]="types" [(ngModel)]="type" optionLabel="label" optionValue="value" /></label>
          <div class="row">
            <label><span>From</span><app-date-input [(ngModel)]="from" /></label>
            <label><span>To</span><app-date-input [(ngModel)]="to" /></label>
          </div>
          <label><span>Note</span><textarea pTextarea rows="3" [(ngModel)]="note"></textarea></label>
          <label><span>Document {{ type === 'MC' ? '(required, max 5 MB)' : '(optional, max 5 MB)' }}</span><input type="file" accept="image/*,application/pdf" (change)="pick($event)" /></label>
          @if (fileName) { <div class="muted small">{{ fileName }} · {{ (fileSize / 1024) | number: '1.0-0' }} KB</div> }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" [text]="true" (onClick)="submitOpen = false" />
          <p-button label="Submit" icon="pi pi-send" (onClick)="submit()" [loading]="busy()" [disabled]="!from || !to" />
        </ng-template>
      </p-dialog>
    }
  `,
  styles: `
    .ma-page { display: grid; gap: 1rem; grid-template-columns: minmax(0, 1fr); }
    .ma-head { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem; .spacer { flex: 1; } }
    .ma-name { font-size: 1.1rem; font-weight: 600; color: var(--ut-ink); }
    .ma-overall { display: grid; justify-items: center; padding: 0.3rem 0.9rem; border-radius: 10px; border: 1px solid var(--ut-border);
      span { font-size: 1.5rem; font-weight: 700; } small { font-size: 0.7rem; color: var(--ut-muted); }
      &.ok span { color: #15803d; } &.warn span { color: #b45309; } &.bad span { color: #c81e14; } }
    h2 { font-size: 0.95rem; margin: 0 0 0.5rem; display: flex; gap: 0.4rem; align-items: center; }
    .muted { color: var(--ut-muted); font-size: 0.82rem; } .small { font-size: 0.75rem; } .r { text-align: right; } td.wrap { white-space: normal; }
    .bar { position: relative; height: 1.2rem; background: var(--ut-surface); border-radius: 4px; overflow: hidden; margin-bottom: 0.2rem;
      .fill { height: 100%; &.ok { background: rgba(22,163,74,0.45); } &.warn { background: rgba(217,119,6,0.5); } &.bad { background: rgba(200,30,20,0.5); } }
      span { position: absolute; left: 0.4rem; top: 0; font-size: 0.75rem; line-height: 1.2rem; font-weight: 600; } }
    .cnt { border-radius: 6px; padding: 0.05rem 0.4rem; font-size: 0.75rem; font-weight: 600;
      &.present { background: rgba(22,163,74,0.14); color: #15803d; } &.late { background: rgba(217,119,6,0.14); color: #b45309; }
      &.absent { background: rgba(200,30,20,0.12); color: #c81e14; } &.excused { background: rgba(37,99,235,0.12); color: #1d4ed8; } }
    .ma-form { display: grid; gap: 0.7rem; label { display: grid; gap: 0.25rem; font-size: 0.78rem; font-weight: 600; color: var(--ut-muted); }
      .row { display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem; } textarea, input[pInputText] { width: 100%; } }
  `,
})
export class MyAttendance implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<MyAttendanceResponse | null>(null);

  protected submitOpen = false;
  protected type = 'MC';
  protected from = '';
  protected to = '';
  protected note = '';
  protected fileName = '';
  protected fileType = '';
  protected fileSize = 0;
  private fileBase64 = '';
  protected readonly types = [
    { label: 'Medical certificate', value: 'MC' },
    { label: 'Leave of absence', value: 'LEAVE' },
    { label: 'Official activity (school / national)', value: 'OFFICIAL' },
  ];

  ngOnInit(): void {
    this.page.set('My Attendance');
    this.load();
  }

  private load(): void {
    this.rpc.execute<MyAttendanceResponse>('MyAttendanceRequest', { operation: 'LOAD' }).subscribe({
      next: (r) => { this.data.set(r); this.loading.set(false); },
      error: (e: ApiError) => { this.error.set(e.message); this.loading.set(false); },
    });
  }

  protected rate(r?: number): string {
    const c = this.data()?.config;
    if (r == null || !c) return '';
    if (c.eligibilityPct != null && r < c.eligibilityPct) return 'bad';
    return r < c.minPct ? 'warn' : 'ok';
  }

  protected standing(c: ClassStat): string {
    return c.standing === 'ELIGIBILITY_RISK' ? 'Examination eligibility at risk' : c.standing === 'BELOW_MIN' ? 'Below minimum' : '';
  }

  protected typeLabel(t?: string): string {
    return this.types.find((x) => x.value === t)?.label ?? t ?? '';
  }

  protected openSubmit(): void {
    this.type = 'MC'; this.from = ''; this.to = ''; this.note = ''; this.fileName = ''; this.fileBase64 = ''; this.fileSize = 0;
    this.submitOpen = true;
  }

  protected pick(ev: Event): void {
    const f = (ev.target as HTMLInputElement).files?.[0];
    if (!f) return;
    if (f.size > 5 * 1024 * 1024) { this.messages.add({ severity: 'error', summary: 'Document', detail: 'The document is larger than 5 MB.' }); return; }
    const reader = new FileReader();
    reader.onload = () => {
      const s = String(reader.result);
      this.fileBase64 = s.substring(s.indexOf(',') + 1);
      this.fileName = f.name; this.fileType = f.type || 'application/octet-stream'; this.fileSize = f.size;
    };
    reader.readAsDataURL(f);
  }

  protected submit(): void {
    this.busy.set(true);
    const excuse = { type: this.type, from: this.from, to: this.to, note: this.note, fileName: this.fileName || undefined, fileType: this.fileType || undefined, fileBase64: this.fileBase64 || undefined };
    this.rpc.execute<MyAttendanceResponse>('MyAttendanceRequest', { operation: 'SUBMIT_EXCUSE', excuse }).subscribe({
      next: (r) => {
        this.busy.set(false); this.submitOpen = false; this.data.set(r);
        this.messages.add({ severity: 'success', summary: 'Submitted', detail: r.message ?? 'Submitted for approval.' });
      },
      error: (e: ApiError) => { this.busy.set(false); this.messages.add({ severity: 'error', summary: 'Not submitted', detail: e.message, life: 9000 }); },
    });
  }
}
