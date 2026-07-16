import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Create / Edit Department Status Types (legacy Struts deptStatusTypeEdit page).
 * Backed by the StatusTypeEditRequest command bean: LOAD returns every status
 * type with its editable fields, SAVE upserts one record, DELETE removes one.
 *
 * `apply` and `status` are integer bitmasks; both are edited here as a set of
 * named checkboxes decomposed on LOAD and re-composed on SAVE. The bit values
 * mirror DepartmentStatusType.Apply / DepartmentStatusType.Status ordinals.
 */

// --- request / response DTOs (inline; match Gson field naming iField -> field) ---
type Operation = 'LOAD' | 'SAVE' | 'DELETE';

interface StatusTypeRecord {
  id?: number | null;
  reference?: string;
  label?: string;
  apply?: number;
  status?: number;
  ord?: number | null;
}

interface StatusTypeEditRequest {
  operation: Operation;
  record?: StatusTypeRecord;
}

interface StatusTypeEditResponse {
  editable?: boolean;
  addable?: boolean;
  deletable?: boolean;
  records?: StatusTypeRecord[];
}

interface FlagDef {
  key: string;
  label: string;
  bit: number;
}

/** DepartmentStatusType.Apply ordinals: Session=1<<0, Department=1<<1, ExamStatus=1<<2. */
const APPLY_FLAGS: FlagDef[] = [
  { key: 'a_session', label: 'Academic Session', bit: 1 << 0 },
  { key: 'a_department', label: 'Department', bit: 1 << 1 },
  { key: 'a_examStatus', label: 'Examinations', bit: 1 << 2 },
];

/** DepartmentStatusType.Status ordinals (bit = 1 << ordinal). */
const STATUS_FLAGS: FlagDef[] = [
  { key: 'ManagerView', label: 'Manager can view', bit: 1 << 0 },
  { key: 'ManagerEdit', label: 'Manager can edit', bit: 1 << 1 },
  { key: 'ManagerLimitedEdit', label: 'Manager limited edit', bit: 1 << 2 },
  { key: 'OwnerView', label: 'Owner can view', bit: 1 << 3 },
  { key: 'OwnerEdit', label: 'Owner can edit', bit: 1 << 4 },
  { key: 'OwnerLimitedEdit', label: 'Owner limited edit', bit: 1 << 5 },
  { key: 'Audit', label: 'Audit', bit: 1 << 6 },
  { key: 'Timetable', label: 'Timetable', bit: 1 << 7 },
  { key: 'Commit', label: 'Commit', bit: 1 << 8 },
  { key: 'ExamView', label: 'Examinations can view', bit: 1 << 9 },
  { key: 'ExamEdit', label: 'Examinations can edit', bit: 1 << 10 },
  { key: 'ExamTimetable', label: 'Examinations timetable', bit: 1 << 11 },
  { key: 'ReportExamsFinal', label: 'Report final exams', bit: 1 << 12 },
  { key: 'ReportExamsMidterm', label: 'Report midterm exams', bit: 1 << 13 },
  { key: 'ReportClasses', label: 'Report classes', bit: 1 << 14 },
  { key: 'StudentsAssistant', label: 'Student scheduling assistant', bit: 1 << 15 },
  { key: 'StudentsPreRegister', label: 'Student pre-registration', bit: 1 << 16 },
  { key: 'StudentsOnline', label: 'Online student scheduling', bit: 1 << 17 },
  { key: 'TestSession', label: 'Test session', bit: 1 << 18 },
  { key: 'AllowNoRole', label: 'Allow no-role access', bit: 1 << 19 },
  { key: 'AllowRollForward', label: 'Allow roll forward', bit: 1 << 20 },
  { key: 'EventManagement', label: 'Event management', bit: 1 << 21 },
  { key: 'InstructorSurvey', label: 'Instructor survey', bit: 1 << 22 },
];

interface Editing {
  id: number | null;
  reference: string;
  label: string;
  ord: number | null;
  bits: Record<string, boolean>;
}

@Component({
  selector: 'app-status-types-edit',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    CheckboxModule,
    DialogModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './status-types-edit.html',
})
export class StatusTypesEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly applyFlags = APPLY_FLAGS;
  protected readonly statusFlags = STATUS_FLAGS;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly records = signal<StatusTypeRecord[]>([]);
  protected readonly editable = signal(false);
  protected readonly addable = signal(false);
  protected readonly deletable = signal(false);

  protected readonly dialogVisible = signal(false);
  protected editing: Editing = this.blank();

  constructor() {
    this.page.set('Status Types');
    this.reload();
  }

  private blank(): Editing {
    return { id: null, reference: '', label: '', ord: null, bits: {} };
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.send({ operation: 'LOAD' }, () => this.loading.set(false), (e) => {
      this.error.set(e.message);
      this.loading.set(false);
    });
  }

  private send(request: StatusTypeEditRequest, done: () => void, fail: (e: ApiError) => void): void {
    this.rpc.execute<StatusTypeEditResponse>('StatusTypeEditRequest', request).subscribe({
      next: (res) => {
        this.records.set(res.records ?? []);
        this.editable.set(!!res.editable);
        this.addable.set(!!res.addable);
        this.deletable.set(!!res.deletable);
        done();
      },
      error: (e: ApiError) => fail(e),
    });
  }

  applySummary(r: StatusTypeRecord): string {
    const parts = APPLY_FLAGS.filter((f) => ((r.apply ?? 0) & f.bit) === f.bit).map((f) => f.label);
    return parts.length ? parts.join(', ') : '—';
  }

  rightsSummary(r: StatusTypeRecord): string {
    const parts = STATUS_FLAGS.filter((f) => ((r.status ?? 0) & f.bit) === f.bit).map((f) => f.label);
    return parts.length ? parts.join(', ') : '—';
  }

  add(): void {
    this.editing = this.blank();
    this.dialogVisible.set(true);
  }

  edit(r: StatusTypeRecord): void {
    const bits: Record<string, boolean> = {};
    for (const f of APPLY_FLAGS) bits[f.key] = ((r.apply ?? 0) & f.bit) === f.bit;
    for (const f of STATUS_FLAGS) bits[f.key] = ((r.status ?? 0) & f.bit) === f.bit;
    this.editing = {
      id: r.id ?? null,
      reference: r.reference ?? '',
      label: r.label ?? '',
      ord: r.ord ?? null,
      bits,
    };
    this.dialogVisible.set(true);
  }

  cancel(): void {
    this.dialogVisible.set(false);
  }

  submit(): void {
    const ref = this.editing.reference.trim();
    const label = this.editing.label.trim();
    if (!ref) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Reference is required.' });
      return;
    }
    if (!label) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Label is required.' });
      return;
    }
    let apply = 0;
    for (const f of APPLY_FLAGS) if (this.editing.bits[f.key]) apply |= f.bit;
    let status = 0;
    for (const f of STATUS_FLAGS) if (this.editing.bits[f.key]) status |= f.bit;

    const record: StatusTypeRecord = {
      id: this.editing.id,
      reference: ref,
      label,
      apply,
      status,
      ord: this.editing.ord,
    };

    this.saving.set(true);
    this.send(
      { operation: 'SAVE', record },
      () => {
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.messages.add({ severity: 'success', summary: this.editing.id != null ? 'Saved' : 'Created', detail: `${ref} — ${label}` });
      },
      (e) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    );
  }

  confirmDelete(r: StatusTypeRecord): void {
    this.confirm.confirm({
      header: 'Delete status type',
      message: `Delete "${r.reference}"? Academic sessions using it are reassigned to another session status.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(r),
    });
  }

  private doDelete(r: StatusTypeRecord): void {
    this.send(
      { operation: 'DELETE', record: { id: r.id } },
      () => this.messages.add({ severity: 'success', summary: 'Deleted', detail: r.reference }),
      (e) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    );
  }
}
