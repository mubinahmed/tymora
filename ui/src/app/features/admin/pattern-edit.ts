import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Create / Edit Date Patterns (legacy datePatternEdit.action) and Time Patterns
 * (legacy timePatternEdit.action), unified behind the PatternEditRequest command
 * bean. A `kind` toggle (DATE|TIME) selects the entity for the current academic
 * session; LOAD lists patterns + type options + permission flags, SAVE
 * merges the descriptive fields of one existing pattern, DELETE removes one.
 *
 * DEFERRED (edited only on the legacy JSP page): the DatePattern day bitmap and
 * the TimePattern day/start-slot grid, plus the department / pattern-set
 * associations. Because those complex sub-parts are deferred, creating a new
 * pattern is not offered here — this editor updates existing patterns only.
 */

type Operation = 'LOAD' | 'SAVE' | 'DELETE';
type Kind = 'DATE' | 'TIME';

interface PatternTypeOption {
  id: number;
  label: string;
}

interface PatternRecord {
  id?: number | null;
  name?: string;
  type?: number | null;
  typeLabel?: string;
  visible?: boolean;
  used?: boolean;
  default?: boolean;
  // time only
  nrMeetings?: number | null;
  minPerMtg?: number | null;
  slotsPerMtg?: number | null;
  breakTime?: number | null;
  // date only (read-only)
  patternPreview?: string;
  numberOfWeeks?: string;
}

interface PatternEditRequest {
  kind: Kind;
  operation: Operation;
  record?: PatternRecord;
}

interface PatternEditResponse {
  kind?: Kind;
  editable?: boolean;
  addable?: boolean;
  deletable?: boolean;
  records?: PatternRecord[];
  types?: PatternTypeOption[];
}

interface Editing {
  id: number | null;
  name: string;
  type: number | null;
  visible: boolean;
  nrMeetings: number | null;
  minPerMtg: number | null;
  slotsPerMtg: number | null;
  breakTime: number | null;
}

@Component({
  selector: 'app-pattern-edit',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    CheckboxModule,
    DialogModule,
    SelectModule,
    SelectButtonModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './pattern-edit.html',
})
export class PatternEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly kindOptions = [
    { label: 'Date Patterns', value: 'DATE' as Kind },
    { label: 'Time Patterns', value: 'TIME' as Kind },
  ];
  protected readonly kind = signal<Kind>('DATE');
  protected readonly isTime = computed(() => this.kind() === 'TIME');

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly records = signal<PatternRecord[]>([]);
  protected readonly types = signal<PatternTypeOption[]>([]);
  protected readonly editable = signal(false);
  protected readonly deletable = signal(false);

  protected readonly dialogVisible = signal(false);
  protected editing: Editing = this.blank();

  constructor() {
    this.page.set('Date & Time Patterns');
    this.reload();
  }

  private blank(): Editing {
    return {
      id: null,
      name: '',
      type: null,
      visible: true,
      nrMeetings: null,
      minPerMtg: null,
      slotsPerMtg: null,
      breakTime: null,
    };
  }

  switchKind(kind: Kind): void {
    if (kind === this.kind()) return;
    this.kind.set(kind);
    this.page.set(kind === 'TIME' ? 'Time Patterns' : 'Date Patterns');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.send({ kind: this.kind(), operation: 'LOAD' }, () => this.loading.set(false), (e) => {
      this.error.set(e.message);
      this.loading.set(false);
    });
  }

  private send(request: PatternEditRequest, done: () => void, fail: (e: ApiError) => void): void {
    this.rpc.execute<PatternEditResponse>('PatternEditRequest', request).subscribe({
      next: (res) => {
        this.records.set(res.records ?? []);
        this.types.set(res.types ?? []);
        this.editable.set(!!res.editable);
        this.deletable.set(!!res.deletable);
        done();
      },
      error: (e: ApiError) => fail(e),
    });
  }

  typeLabel(id: number | null | undefined): string {
    if (id == null) return '';
    return this.types().find((t) => t.id === id)?.label ?? String(id);
  }

  edit(r: PatternRecord): void {
    if (!this.editable()) return;
    this.editing = {
      id: r.id ?? null,
      name: r.name ?? '',
      type: r.type ?? null,
      visible: r.visible !== false,
      nrMeetings: r.nrMeetings ?? null,
      minPerMtg: r.minPerMtg ?? null,
      slotsPerMtg: r.slotsPerMtg ?? null,
      breakTime: r.breakTime ?? null,
    };
    this.dialogVisible.set(true);
  }

  cancel(): void {
    this.dialogVisible.set(false);
  }

  submit(): void {
    const name = this.editing.name.trim();
    if (!name) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Name is required.' });
      return;
    }

    const record: PatternRecord = {
      id: this.editing.id,
      name,
      type: this.editing.type,
      visible: this.editing.visible,
    };
    if (this.isTime()) {
      record.nrMeetings = this.editing.nrMeetings;
      record.minPerMtg = this.editing.minPerMtg;
      record.slotsPerMtg = this.editing.slotsPerMtg;
      record.breakTime = this.editing.breakTime;
    }

    this.saving.set(true);
    this.send(
      { kind: this.kind(), operation: 'SAVE', record },
      () => {
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: name });
      },
      (e) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    );
  }

  confirmDelete(r: PatternRecord): void {
    this.confirm.confirm({
      header: 'Delete pattern',
      message: `Delete "${r.name}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(r),
    });
  }

  private doDelete(r: PatternRecord): void {
    this.send(
      { kind: this.kind(), operation: 'DELETE', record: { id: r.id } },
      () => this.messages.add({ severity: 'success', summary: 'Deleted', detail: r.name }),
      (e) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    );
  }
}
