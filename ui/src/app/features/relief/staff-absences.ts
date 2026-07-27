import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Relief Planning "Staff Absences" (admin). Lists every staff absence over a date
 * range and lets an administrator record, edit, approve/reject or remove them.
 * Approved absences feed the relief-generation engine.
 */
type Operation = 'LOAD' | 'SAVE' | 'DELETE' | 'APPROVE' | 'REJECT';

interface AbsenceInfo {
  id?: number | null;
  uid?: string;
  name?: string;
  reasonId?: number | null;
  reasonLabel?: string;
  startDate?: string;
  endDate?: string;
  note?: string;
  status?: number;
  statusLabel?: string;
  canEdit?: boolean;
}
interface Option { id: string; label: string; }
interface StaffAbsenceRequest { operation: Operation; from?: string; to?: string; mineOnly?: boolean; id?: number; absence?: AbsenceInfo; }
interface StaffAbsenceResponse { absences?: AbsenceInfo[]; reasons?: Option[]; staff?: Option[]; canManage?: boolean; myUid?: string; }

@Component({
  selector: 'app-staff-absences',
  imports: [
    FormsModule, TableModule, ButtonModule, SelectModule, DatePickerModule, InputTextModule,
    TextareaModule, TooltipModule, DialogModule, TagModule, MessageModule, ProgressSpinnerModule, ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  template: `
    <div class="page">
      <div class="toolbar">
        <div class="left">
          <div><label>From</label><p-datepicker [(ngModel)]="from" dataType="string" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" (onSelect)="load()" /></div>
          <div><label>To</label><p-datepicker [(ngModel)]="to" dataType="string" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" (onSelect)="load()" /></div>
          <p-button label="Refresh" icon="pi pi-refresh" severity="secondary" [text]="true" (onClick)="load()" />
        </div>
        @if (canManage()) { <p-button label="Add absence" icon="pi pi-plus" (onClick)="add()" /> }
      </div>

      @if (loading()) {
        <div class="center"><p-progressSpinner strokeWidth="4" /></div>
      } @else if (error()) {
        <p-message severity="error" [text]="error()!" />
      } @else {
        <p-table [value]="absences()" styleClass="p-datatable-sm p-datatable-gridlines" [rowHover]="true">
          <ng-template pTemplate="header">
            <tr><th>Staff</th><th>Reason</th><th>From</th><th>To</th><th>Note</th><th>Status</th><th style="width: 14rem;"></th></tr>
          </ng-template>
          <ng-template pTemplate="body" let-a>
            <tr>
              <td>{{ a.name }}</td>
              <td>{{ a.reasonLabel }}</td>
              <td>{{ a.startDate }}</td>
              <td>{{ a.endDate }}</td>
              <td>{{ a.note }}</td>
              <td><p-tag [value]="a.statusLabel" [severity]="statusSeverity(a.status)" /></td>
              <td class="actions">
                @if (a.status === 0 && canManage()) {
                  <p-button icon="pi pi-check" severity="success" [text]="true" pTooltip="Approve" (onClick)="setStatus('APPROVE', a)" />
                  <p-button icon="pi pi-times" severity="danger" [text]="true" pTooltip="Reject" (onClick)="setStatus('REJECT', a)" />
                }
                @if (a.canEdit) {
                  <p-button icon="pi pi-pencil" [text]="true" (onClick)="edit(a)" />
                  <p-button icon="pi pi-trash" severity="danger" [text]="true" (onClick)="confirmDelete(a)" />
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage"><tr><td colspan="7">No absences in this range.</td></tr></ng-template>
        </p-table>
      }

      <p-dialog [(visible)]="dialogVisible" [modal]="true" [style]="{ width: '32rem' }"
        [header]="editing.id ? 'Edit absence' : 'Add absence'">
        <div class="form">
          <label>Staff member *</label>
          <p-select [(ngModel)]="editing.uid" [options]="staff()" optionLabel="label" optionValue="id"
            appendTo="body" [filter]="true" filterBy="label" placeholder="Select staff" styleClass="w-full" />
          <label>Reason</label>
          <p-select [(ngModel)]="editing.reasonId" [options]="reasons()" optionLabel="label" optionValue="id"
            appendTo="body" [showClear]="true" placeholder="Select reason" styleClass="w-full" />
          <div class="row">
            <div><label>From *</label><p-datepicker [(ngModel)]="editing.startDate" dataType="string" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" styleClass="w-full" /></div>
            <div><label>To *</label><p-datepicker [(ngModel)]="editing.endDate" dataType="string" dateFormat="yy-mm-dd" [showIcon]="true" appendTo="body" styleClass="w-full" /></div>
          </div>
          <label>Note</label>
          <textarea pTextarea [(ngModel)]="editing.note" rows="2" class="w-full"></textarea>
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" [text]="true" (onClick)="dialogVisible.set(false)" />
          <p-button label="Save" icon="pi pi-check" (onClick)="submit()" [loading]="saving()" />
        </ng-template>
      </p-dialog>

      <p-confirmDialog />
    </div>
  `,
  styles: [`
    .page { padding: 1rem; display: flex; flex-direction: column; gap: 1rem; }
    .toolbar { display: flex; justify-content: space-between; align-items: end; gap: 1rem; flex-wrap: wrap; }
    .toolbar .left { display: flex; align-items: end; gap: .75rem; }
    label { display: block; font-size: .8rem; opacity: .8; margin: .5rem 0 .25rem; }
    .actions { display: flex; gap: .1rem; }
    .center { display: flex; justify-content: center; padding: 3rem; }
    .form { display: flex; flex-direction: column; }
    .form .row { display: flex; gap: 1rem; }
    .form .row > div { flex: 1; }
  `],
})
export class StaffAbsences {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly mineOnly = false;
  protected readonly from = signal<string>('');
  protected readonly to = signal<string>('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly absences = signal<AbsenceInfo[]>([]);
  protected readonly reasons = signal<Option[]>([]);
  protected readonly staff = signal<Option[]>([]);
  protected readonly canManage = signal(false);

  protected readonly dialogVisible = signal(false);
  protected editing: AbsenceInfo = {};

  constructor() {
    this.page.set('Staff Absences');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.send({ operation: 'LOAD', from: this.from() || undefined, to: this.to() || undefined, mineOnly: this.mineOnly },
      () => this.loading.set(false),
      (e) => { this.error.set(e.message); this.loading.set(false); });
  }

  add(): void { this.editing = { status: 1 }; this.dialogVisible.set(true); }
  edit(a: AbsenceInfo): void { this.editing = { ...a }; this.dialogVisible.set(true); }

  submit(): void {
    if (!this.editing.uid && !this.mineOnly) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'A staff member is required.' });
      return;
    }
    if (!this.editing.startDate || !this.editing.endDate) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Start and end dates are required.' });
      return;
    }
    this.saving.set(true);
    this.send({ operation: 'SAVE', from: this.from() || undefined, to: this.to() || undefined, mineOnly: this.mineOnly, absence: this.editing },
      () => { this.saving.set(false); this.dialogVisible.set(false); this.messages.add({ severity: 'success', summary: 'Saved' }); },
      (e) => { this.saving.set(false); this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message }); });
  }

  setStatus(op: 'APPROVE' | 'REJECT', a: AbsenceInfo): void {
    this.send({ operation: op, id: a.id!, from: this.from() || undefined, to: this.to() || undefined, mineOnly: this.mineOnly },
      () => this.messages.add({ severity: 'success', summary: op === 'APPROVE' ? 'Approved' : 'Rejected', detail: a.name }),
      (e) => this.messages.add({ severity: 'error', summary: 'Failed', detail: e.message }));
  }

  confirmDelete(a: AbsenceInfo): void {
    this.confirm.confirm({
      header: 'Delete absence',
      message: `Delete ${a.name}'s absence (${a.startDate} – ${a.endDate})? Its relief assignments are removed too.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.send({ operation: 'DELETE', id: a.id!, from: this.from() || undefined, to: this.to() || undefined, mineOnly: this.mineOnly },
        () => this.messages.add({ severity: 'success', summary: 'Deleted', detail: a.name }),
        (e) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message })),
    });
  }

  statusSeverity(status?: number): 'success' | 'warn' | 'danger' {
    return status === 1 ? 'success' : status === 2 ? 'danger' : 'warn';
  }

  private send(request: StaffAbsenceRequest, done: () => void, fail: (e: ApiError) => void): void {
    this.rpc.execute<StaffAbsenceResponse>('StaffAbsenceRequest', request).subscribe({
      next: (res) => {
        this.absences.set(res.absences ?? []);
        this.reasons.set(res.reasons ?? []);
        this.staff.set(res.staff ?? []);
        this.canManage.set(!!res.canManage);
        done();
      },
      error: (e: ApiError) => fail(e),
    });
  }
}
