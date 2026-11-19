import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';
import { HandoverNotes, HandoverNoteInfo } from './handover-notes';

/**
 * Teacher self-service "My Relief Duties" (MyReliefDutiesRequest). Read-only view
 * of the current user's own covering assignments, together with the absent
 * teacher's shared relief handover — instructions, a link, and/or an attachment —
 * for use while covering the lesson (ITQ 6.6).
 */
interface DutyInfo {
  id: number;
  meetingDate: string;
  absentName: string;
  reasonLabel: string;
  className: string;
  timeText: string;
  roomName: string;
  handoverNotes: HandoverNoteInfo[];
}
interface MyReliefDutiesRequest { operation: 'LOAD'; from?: string; to?: string; }
interface MyReliefDutiesResponse { duties?: DutyInfo[]; }

@Component({
  selector: 'app-my-relief-duties',
  imports: [FormsModule, TableModule, ButtonModule, DatePickerModule, DialogModule, MessageModule, ProgressSpinnerModule, HandoverNotes],
  template: `
    <div class="page">
      <div class="toolbar">
        <h2>My relief duties</h2>
        <div class="filters">
          <div><label>From</label><p-datepicker [(ngModel)]="from" dataType="string" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" (onSelect)="load()" /></div>
          <div><label>To</label><p-datepicker [(ngModel)]="to" dataType="string" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" (onSelect)="load()" /></div>
          <p-button label="Refresh" icon="pi pi-refresh" severity="secondary" [text]="true" (onClick)="load()" />
        </div>
      </div>

      @if (loading()) {
        <div class="center"><p-progressSpinner strokeWidth="4" /></div>
      } @else if (error()) {
        <p-message severity="error" [text]="error()!" />
      } @else {
        <p-table [value]="duties()" styleClass="p-datatable-sm p-datatable-gridlines" [rowHover]="true">
          <ng-template pTemplate="header">
            <tr><th>Date</th><th>Time</th><th>Class</th><th>Venue</th><th>Absent teacher</th><th>Reason</th><th style="width: 10rem;"></th></tr>
          </ng-template>
          <ng-template pTemplate="body" let-d>
            <tr>
              <td>{{ d.meetingDate }}</td>
              <td>{{ d.timeText }}</td>
              <td>{{ d.className }}</td>
              <td>{{ d.roomName }}</td>
              <td>{{ d.absentName }}</td>
              <td>{{ d.reasonLabel }}</td>
              <td>
                <p-button [label]="'Handover' + (d.handoverNotes?.length ? ' (' + d.handoverNotes.length + ')' : '')"
                  icon="pi pi-share-alt" [text]="true" size="small" (onClick)="openHandover(d)" />
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage"><tr><td colspan="7">No relief duties in this range.</td></tr></ng-template>
        </p-table>
      }

      <p-dialog [(visible)]="handoverVisible" [modal]="true" [style]="{ width: '34rem' }" header="Relief handover instructions">
        @if (handoverTarget) {
          <p class="hint">Shared by {{ handoverTarget.absentName }} for {{ handoverTarget.className }} on {{ handoverTarget.meetingDate }} ({{ handoverTarget.timeText }}).</p>
          <app-handover-notes [notes]="handoverTarget.handoverNotes" [canAdd]="false" />
        }
        <ng-template pTemplate="footer">
          <p-button label="Close" severity="secondary" [text]="true" (onClick)="handoverVisible.set(false)" />
        </ng-template>
      </p-dialog>
    </div>
  `,
  styles: [`
    .page { padding: 1rem; display: flex; flex-direction: column; gap: 1rem; }
    .toolbar { display: flex; justify-content: space-between; align-items: end; gap: 1rem; flex-wrap: wrap; }
    .filters { display: flex; align-items: end; gap: .75rem; }
    label { display: block; font-size: .8rem; opacity: .8; margin-bottom: .25rem; }
    .center { display: flex; justify-content: center; padding: 3rem; }
    .hint { font-size: .85rem; opacity: .7; margin-top: 0; }
  `],
})
export class MyReliefDuties {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly from = signal<string>('');
  protected readonly to = signal<string>('');
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly duties = signal<DutyInfo[]>([]);

  protected readonly handoverVisible = signal(false);
  protected handoverTarget: DutyInfo | null = null;

  constructor() {
    this.page.set('My Relief Duties');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: MyReliefDutiesRequest = { operation: 'LOAD', from: this.from() || undefined, to: this.to() || undefined };
    this.rpc.execute<MyReliefDutiesResponse>('MyReliefDutiesRequest', request).subscribe({
      next: (res) => { this.duties.set(res.duties ?? []); this.loading.set(false); },
      error: (e: ApiError) => { this.error.set(e.message); this.loading.set(false); },
    });
  }

  openHandover(d: DutyInfo): void { this.handoverTarget = d; this.handoverVisible.set(true); }
}
