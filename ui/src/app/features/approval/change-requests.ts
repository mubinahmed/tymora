import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ChangeRequestsService } from '../../core/change-requests.service';
import { ApiError } from '../../core/models';
import { AssignmentChangeInfo, ChangeStatus } from './change-requests.models';
import { ClassAssignmentDialog } from '../solver/class-assignment-dialog';
import { ExamAssignmentDialog } from '../examinations/exam-assignment-dialog';

const STATUS: Record<ChangeStatus, { label: string; severity: 'warn' | 'success' | 'danger' | 'secondary' | 'info' }> = {
  PENDING: { label: 'Pending approval', severity: 'warn' },
  APPLIED: { label: 'Applied', severity: 'success' },
  REJECTED: { label: 'Rejected', severity: 'danger' },
  CANCELLED: { label: 'Withdrawn', severity: 'secondary' },
  FAILED: { label: 'Approved, not applied', severity: 'danger' },
};

/**
 * Change Requests: the approval step for class and examination assignment changes.
 * Approvers see the requests awaiting their decision and approve (the change is applied) or
 * reject them with a note; requesters follow their own requests and can withdraw pending ones.
 * "All changes" is the audit trail: every change made from the assignment dialogs with its
 * reason, requester, decision and outcome. Backed by AssignmentChangeRequest.
 */
@Component({
  selector: 'app-change-requests',
  imports: [
    DatePipe,
    FormsModule,
    ButtonModule,
    TableModule,
    TagModule,
    MessageModule,
    DialogModule,
    TextareaModule,
    SelectButtonModule,
    ProgressSpinnerModule,
    TooltipModule,
    ClassAssignmentDialog,
    ExamAssignmentDialog,
  ],
  templateUrl: './change-requests.html',
  styleUrl: './change-requests.scss',
})
export class ChangeRequests implements OnInit {
  private page = inject(PageService);
  private messages = inject(MessageService);
  protected changes = inject(ChangeRequestsService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = signal<AssignmentChangeInfo[]>([]);
  protected filter = 'PENDING';

  protected readonly canApprove = computed(() => !!this.changes.info()?.canApprove);
  protected readonly filters = computed(() =>
    this.canApprove()
      ? [
          { label: `Awaiting my decision (${this.changes.pendingCount()})`, value: 'PENDING' },
          { label: 'My changes', value: 'MINE' },
          { label: 'All changes (audit)', value: 'ALL' },
        ]
      : [
          { label: `My pending (${this.changes.info()?.myPendingCount ?? 0})`, value: 'PENDING' },
          { label: 'All my changes', value: 'MINE' },
        ],
  );

  // decision dialog
  protected readonly deciding = signal<{ row: AssignmentChangeInfo; approve: boolean } | null>(null);
  protected note = '';
  protected readonly saving = signal(false);

  // open the subject in its assignment dialog
  protected readonly classDialog = signal(false);
  protected readonly examDialog = signal(false);
  protected readonly subjectId = signal<number | null>(null);

  ngOnInit(): void {
    this.page.set('Change Requests');
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.changes.call({ operation: 'LIST', filter: this.filter }).subscribe({
      next: (r) => {
        this.rows.set(r.changes ?? []);
        this.loading.set(false);
        // the requester has now seen the decisions on their requests
        if ((r.unseenDecisions ?? 0) > 0 && this.filter !== 'PENDING')
          this.changes.call({ operation: 'MARK_SEEN' }).subscribe({ error: () => {} });
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  protected onFilter(): void {
    this.load();
  }

  protected status(s: ChangeStatus | undefined) {
    return STATUS[s ?? 'PENDING'] ?? STATUS.PENDING;
  }

  protected summaryLines(r: AssignmentChangeInfo): string[] {
    return (r.summary ?? '').split('\n').filter((l) => l.trim());
  }

  protected modeLabel(r: AssignmentChangeInfo): string {
    if (r.type === 'CLASS') return 'Class · solver solution';
    return r.mode === 'SOLVER' ? 'Exam · solver solution' : 'Exam · database';
  }

  protected openDecision(row: AssignmentChangeInfo, approve: boolean): void {
    this.note = '';
    this.deciding.set({ row, approve });
  }

  protected noteValid(): boolean {
    const d = this.deciding();
    return !!d && (d.approve || this.note.trim().length > 0);
  }

  protected decide(): void {
    const d = this.deciding();
    if (!d || !this.noteValid()) return;
    this.saving.set(true);
    this.changes.call({ operation: d.approve ? 'APPROVE' : 'REJECT', id: d.row.id, note: this.note.trim() }).subscribe({
      next: (r) => {
        this.saving.set(false);
        this.deciding.set(null);
        const st = r.change?.status;
        this.messages.add({
          severity: st === 'APPLIED' ? 'success' : st === 'FAILED' ? 'error' : 'info',
          summary: st === 'APPLIED' ? 'Approved and applied' : st === 'FAILED' ? 'Approved, but not applied' : 'Rejected',
          detail: st === 'FAILED' ? r.change?.resultMessage : `${r.change?.subjectName}: the requester is notified.`,
          life: st === 'FAILED' ? 15000 : 6000,
        });
        this.load();
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Decision not saved', detail: e.message });
      },
    });
  }

  protected withdraw(row: AssignmentChangeInfo): void {
    this.changes.call({ operation: 'CANCEL', id: row.id }).subscribe({
      next: () => {
        this.messages.add({ severity: 'info', summary: 'Request withdrawn', detail: row.subjectName });
        this.load();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Not withdrawn', detail: e.message }),
    });
  }

  protected openSubject(row: AssignmentChangeInfo): void {
    if (row.subjectId == null) return;
    this.subjectId.set(row.subjectId);
    if (row.type === 'CLASS') this.classDialog.set(true);
    else this.examDialog.set(true);
  }
}
