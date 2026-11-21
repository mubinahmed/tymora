import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TableModule } from 'primeng/table';
import { TextareaModule } from 'primeng/textarea';
import { CheckboxModule } from 'primeng/checkbox';
import { AllocationStatus, LogEntry } from './seating.models';

/**
 * Review / approval / publication panel shared by Examination Seating and Invigilation:
 * the status of the plan, approve / publish / reopen (approvers; override of mandatory problems),
 * and the audit trail (who did what, when).
 */
@Component({
  selector: 'app-publication-panel',
  imports: [DatePipe, FormsModule, ButtonModule, TagModule, TableModule, TextareaModule, CheckboxModule],
  template: `
    <div class="pp">
      <div class="pp-state">
        <p-tag [value]="label()" [severity]="severity()" />
        @if (status()?.approvedBy) { <span class="muted">Approved by {{ status()!.approvedBy }}, {{ status()!.approvedTs | date: 'd MMM y, HH:mm' }}</span> }
        @if (status()?.publishedBy) { <span class="muted">· Published (version {{ status()!.version }}) by {{ status()!.publishedBy }}, {{ status()!.publishedTs | date: 'd MMM y, HH:mm' }}</span> }
        @if (status()?.changesSincePublication) { <span class="warn">· {{ status()!.changesSincePublication }} change(s) since the last publication</span> }
      </div>
      @if (nrErrors() > 0) {
        <div class="pp-errors"><i class="pi pi-exclamation-triangle"></i> {{ nrErrors() }} mandatory problem(s) block approval and publication. Fix them, or approve / publish with override.</div>
      }
      @if (status()?.canApprove) {
        <div class="pp-actions">
          <textarea pTextarea [(ngModel)]="note" rows="2" placeholder="Note (optional, recorded with the decision)" style="width: min(36rem, 100%)"></textarea>
          <label class="chk"><p-checkbox [(ngModel)]="override" [binary]="true" /> Override mandatory problems</label>
          <p-button label="Approve" icon="pi pi-check" severity="secondary" (onClick)="act('APPROVE')" [disabled]="busy()" />
          <p-button label="Publish" icon="pi pi-send" (onClick)="act('PUBLISH')" [disabled]="busy()" />
          @if (status()?.status !== 'DRAFT') { <p-button label="Reopen" icon="pi pi-undo" severity="secondary" [text]="true" (onClick)="act('REOPEN')" [disabled]="busy()" /> }
        </div>
      } @else {
        <div class="muted">Approval and publication are done by an approver (Administrator or Session Administrator).</div>
      }
      <div class="pp-log-head">
        <h4>History</h4>
        <p-button label="Refresh" icon="pi pi-refresh" size="small" [text]="true" (onClick)="loadLog.emit()" />
      </div>
      <p-table [value]="log() ?? []" styleClass="p-datatable-sm" [paginator]="(log()?.length ?? 0) > 25" [rows]="25">
        <ng-template pTemplate="header"><tr><th style="width:10rem">When</th><th style="width:10rem">Who</th><th style="width:9rem">Action</th><th>Detail</th></tr></ng-template>
        <ng-template pTemplate="body" let-l>
          <tr><td>{{ l.timeStamp | date: 'd MMM, HH:mm:ss' }}</td><td>{{ l.userName }}</td><td>{{ l.action }}</td><td class="wrap">{{ l.period ? l.period + ': ' : '' }}{{ l.detail }}</td></tr>
        </ng-template>
        <ng-template pTemplate="emptymessage"><tr><td colspan="4">No history yet.</td></tr></ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .pp { display: grid; gap: 0.8rem; }
    .pp-state { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; font-size: 0.85rem; }
    .muted { color: var(--ut-muted); font-size: 0.82rem; }
    .warn { color: #c49a0a; font-size: 0.82rem; font-weight: 600; }
    .pp-errors { color: #c81e14; font-size: 0.85rem; display: flex; gap: 0.4rem; align-items: center; }
    .pp-actions { display: flex; flex-wrap: wrap; gap: 0.6rem; align-items: center; }
    .chk { display: inline-flex; gap: 0.4rem; align-items: center; font-size: 0.85rem; }
    .pp-log-head { display: flex; align-items: center; gap: 0.5rem; h4 { margin: 0; font-size: 0.9rem; } }
    td.wrap { white-space: normal; }
  `],
})
export class PublicationPanel {
  readonly status = input<AllocationStatus | null | undefined>(null);
  readonly nrErrors = input(0);
  readonly log = input<LogEntry[] | null | undefined>(null);
  readonly busy = input(false);
  readonly action = output<{ op: string; note: string; override: boolean }>();
  readonly loadLog = output<void>();
  protected note = '';
  protected override = false;

  protected label(): string {
    switch (this.status()?.status) {
      case 'APPROVED': return 'Approved';
      case 'PUBLISHED': return 'Published';
      default: return this.status()?.publishedTs ? 'Draft (changes after publication)' : 'Draft';
    }
  }

  protected severity(): 'success' | 'info' | 'warn' | 'secondary' {
    switch (this.status()?.status) {
      case 'APPROVED': return 'info';
      case 'PUBLISHED': return 'success';
      default: return this.status()?.publishedTs ? 'warn' : 'secondary';
    }
  }

  protected act(op: string): void {
    this.action.emit({ op, note: this.note, override: this.override });
    this.note = '';
    this.override = false;
  }
}
