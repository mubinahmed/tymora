import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { DateInput } from '../../shared/date-input';
import { TextareaModule } from 'primeng/textarea';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';
import { HandoverNotes, HandoverNoteInfo } from './handover-notes';
import { DisplayDatePipe } from '../../shared/display-date.pipe';

/**
 * Teacher self-service "My Leave" (StaffAbsenceRequest with mineOnly). A teacher
 * submits their own leave requests (which start pending until an administrator
 * approves them), can withdraw a request while it is still pending, and can share
 * relief handover — detailed instructions, a link, and/or an attachment — for
 * whoever ends up covering their lessons (ITQ 6.6).
 */
type Operation = 'LOAD' | 'SAVE' | 'DELETE';

interface AbsenceInfo {
  id?: number | null;
  reasonId?: number | null;
  reasonLabel?: string;
  startDate?: string;
  endDate?: string;
  note?: string;
  status?: number;
  statusLabel?: string;
  canEdit?: boolean;
  canShareHandover?: boolean;
  notes?: HandoverNoteInfo[];
}
interface Option { id: string; label: string; }
interface StaffAbsenceRequest { operation: Operation; mineOnly: boolean; id?: number; absence?: AbsenceInfo; }
interface StaffAbsenceResponse { absences?: AbsenceInfo[]; reasons?: Option[]; }

@Component({
  selector: 'app-my-leave',
  imports: [DisplayDatePipe, 
    FormsModule, TableModule, ButtonModule, SelectModule, DateInput, TextareaModule,
    DialogModule, TagModule, MessageModule, ProgressSpinnerModule, ConfirmDialogModule, HandoverNotes,
  ],
  providers: [ConfirmationService],
  template: `
    <div class="page">
      <div class="toolbar">
        <h2>My leave requests</h2>
        <p-button label="Request leave" icon="pi pi-plus" (onClick)="add()" />
      </div>

      @if (loading()) {
        <div class="center"><p-progressSpinner strokeWidth="4" /></div>
      } @else if (error()) {
        <p-message severity="error" [text]="error()!" />
      } @else {
        <p-table [value]="absences()" styleClass="p-datatable-sm p-datatable-gridlines" [rowHover]="true">
          <ng-template pTemplate="header">
            <tr><th>Reason</th><th>From</th><th>To</th><th>Note</th><th>Status</th><th style="width: 14rem;"></th></tr>
          </ng-template>
          <ng-template pTemplate="body" let-a>
            <tr>
              <td>{{ a.reasonLabel }}</td>
              <td>{{ a.startDate | displayDate }}</td>
              <td>{{ a.endDate | displayDate }}</td>
              <td>{{ a.note }}</td>
              <td><p-tag [value]="a.statusLabel" [severity]="statusSeverity(a.status)" /></td>
              <td class="actions">
                @if (a.canShareHandover) {
                  <p-button [label]="'Handover' + (a.notes?.length ? ' (' + a.notes!.length + ')' : '')"
                    icon="pi pi-share-alt" [text]="true" size="small" (onClick)="openHandover(a)" />
                }
                @if (a.canEdit) {
                  <p-button icon="pi pi-pencil" [text]="true" (onClick)="edit(a)" />
                  <p-button icon="pi pi-trash" severity="danger" [text]="true" (onClick)="confirmWithdraw(a)" />
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage"><tr><td colspan="6">You have no leave requests.</td></tr></ng-template>
        </p-table>
      }

      <p-dialog [(visible)]="dialogVisible" [modal]="true" [style]="{ width: '30rem' }"
        [header]="editing.id ? 'Edit leave request' : 'Request leave'">
        <div class="form">
          <label>Reason</label>
          <p-select [(ngModel)]="editing.reasonId" [options]="reasons()" optionLabel="label" optionValue="id"
            appendTo="body" [showClear]="true" placeholder="Select reason" styleClass="w-full" />
          <div class="row">
            <div><label>From *</label><app-date-input [(ngModel)]="editing.startDate" styleClass="w-full" /></div>
            <div><label>To *</label><app-date-input [(ngModel)]="editing.endDate" styleClass="w-full" /></div>
          </div>
          <label>Note</label>
          <textarea pTextarea [(ngModel)]="editing.note" rows="2" class="w-full"></textarea>
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" [text]="true" (onClick)="dialogVisible.set(false)" />
          <p-button label="Submit" icon="pi pi-check" (onClick)="submit()" [loading]="saving()" />
        </ng-template>
      </p-dialog>

      <p-dialog [(visible)]="handoverVisible" [modal]="true" [style]="{ width: '34rem' }" header="Relief handover instructions">
        @if (handoverTarget) {
          <p class="hint">Shared with whoever covers your lessons on {{ handoverTarget.startDate | displayDate }} – {{ handoverTarget.endDate | displayDate }}.</p>
          <app-handover-notes [absenceId]="handoverTarget.id!" [notes]="handoverTarget.notes ?? []"
            [canAdd]="!!handoverTarget.canShareHandover" [mineOnly]="true" (notesChange)="onNotesChange(handoverTarget, $event)" />
        }
        <ng-template pTemplate="footer">
          <p-button label="Close" severity="secondary" [text]="true" (onClick)="handoverVisible.set(false)" />
        </ng-template>
      </p-dialog>

      <p-confirmDialog />
    </div>
  `,
  styles: [`
    .page { padding: 1rem; display: flex; flex-direction: column; gap: 1rem; }
    .toolbar { display: flex; justify-content: space-between; align-items: center; }
    label { display: block; font-size: .8rem; opacity: .8; margin: .5rem 0 .25rem; }
    .actions { display: flex; gap: .1rem; flex-wrap: wrap; }
    .center { display: flex; justify-content: center; padding: 3rem; }
    .form { display: flex; flex-direction: column; }
    .form .row { display: flex; gap: 1rem; }
    .form .row > div { flex: 1; }
    .hint { font-size: .85rem; opacity: .7; margin-top: 0; }
  `],
})
export class MyLeave {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly absences = signal<AbsenceInfo[]>([]);
  protected readonly reasons = signal<Option[]>([]);

  protected readonly dialogVisible = signal(false);
  protected editing: AbsenceInfo = {};

  protected readonly handoverVisible = signal(false);
  protected handoverTarget: AbsenceInfo | null = null;

  constructor() {
    this.page.set('My Leave');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.send({ operation: 'LOAD', mineOnly: true }, () => this.loading.set(false),
      (e) => { this.error.set(e.message); this.loading.set(false); });
  }

  add(): void { this.editing = {}; this.dialogVisible.set(true); }
  edit(a: AbsenceInfo): void { this.editing = { ...a }; this.dialogVisible.set(true); }

  openHandover(a: AbsenceInfo): void { this.handoverTarget = a; this.handoverVisible.set(true); }

  onNotesChange(a: AbsenceInfo, notes: HandoverNoteInfo[]): void {
    a.notes = notes;
    this.absences.update((list) => list.map((x) => (x.id === a.id ? { ...x, notes } : x)));
  }

  submit(): void {
    if (!this.editing.startDate || !this.editing.endDate) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Start and end dates are required.' });
      return;
    }
    this.saving.set(true);
    this.send({ operation: 'SAVE', mineOnly: true, absence: this.editing },
      () => { this.saving.set(false); this.dialogVisible.set(false); this.messages.add({ severity: 'success', summary: 'Submitted', detail: 'Your leave request was submitted for approval.' }); },
      (e) => { this.saving.set(false); this.messages.add({ severity: 'error', summary: 'Submit failed', detail: e.message }); });
  }

  confirmWithdraw(a: AbsenceInfo): void {
    this.confirm.confirm({
      header: 'Withdraw request',
      message: `Withdraw your leave request (${a.startDate} – ${a.endDate})?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.send({ operation: 'DELETE', mineOnly: true, id: a.id! },
        () => this.messages.add({ severity: 'success', summary: 'Withdrawn' }),
        (e) => this.messages.add({ severity: 'error', summary: 'Failed', detail: e.message })),
    });
  }

  statusSeverity(status?: number): 'success' | 'warn' | 'danger' {
    return status === 1 ? 'success' : status === 2 ? 'danger' : 'warn';
  }

  private send(request: StaffAbsenceRequest, done: () => void, fail: (e: ApiError) => void): void {
    this.rpc.execute<StaffAbsenceResponse>('StaffAbsenceRequest', request).subscribe({
      next: (res) => { this.absences.set(res.absences ?? []); this.reasons.set(res.reasons ?? []); done(); },
      error: (e: ApiError) => fail(e),
    });
  }
}
