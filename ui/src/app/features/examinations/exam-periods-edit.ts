import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/**
 * Create / Edit Examination Periods (legacy Struts examPeriodEdit.action page).
 * Backed by the ExamPeriodEditRequest command bean: LOAD returns every
 * examination period of the current academic session (optionally filtered by
 * exam type) plus the exam-type and preference-level selectors; SAVE upserts
 * one period; DELETE removes one.
 *
 * A period is edited exactly as the legacy form rendered it: a date, a start
 * time (HHMM), an exam length in minutes, event start/stop offsets in minutes,
 * an exam type and a preference level. The multi-period auto-setup wizard and
 * deletion of periods already assigned to exams remain on the legacy page.
 */

type Operation = 'LOAD' | 'SAVE' | 'DELETE';

interface ExamTypeInfo {
  id?: number;
  label?: string;
}

interface PrefLevelInfo {
  id?: number;
  name?: string;
  prolog?: string;
}

interface ExamPeriodRecord {
  id?: number | null;
  examTypeId?: number | null;
  examTypeLabel?: string;
  date?: string;
  dateLabel?: string;
  start?: number | null;
  startLabel?: string;
  endLabel?: string;
  length?: number | null;
  startOffset?: number | null;
  stopOffset?: number | null;
  prefLevelId?: number | null;
  prefName?: string;
  used?: boolean;
  editable?: boolean;
}

interface ExamPeriodEditRequest {
  operation: Operation;
  examTypeId?: number | null;
  record?: ExamPeriodRecord;
}

interface ExamPeriodEditResponse {
  title?: string;
  editable?: boolean;
  addable?: boolean;
  deletable?: boolean;
  examTypeId?: number | null;
  defaultDate?: string;
  defaultLength?: number;
  examTypes?: ExamTypeInfo[];
  prefLevels?: PrefLevelInfo[];
  records?: ExamPeriodRecord[];
}

interface Editing {
  id: number | null;
  examTypeId: number | null;
  date: string;
  start: number | null;
  length: number | null;
  startOffset: number | null;
  stopOffset: number | null;
  prefLevelId: number | null;
}

@Component({
  selector: 'app-exam-periods-edit',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
    DialogModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './exam-periods-edit.html',
})
export class ExamPeriodsEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly records = signal<ExamPeriodRecord[]>([]);
  protected readonly examTypes = signal<ExamTypeInfo[]>([]);
  protected readonly prefLevels = signal<PrefLevelInfo[]>([]);
  protected readonly editable = signal(false);
  protected readonly addable = signal(false);
  protected readonly deletable = signal(false);
  protected readonly defaultDate = signal<string>('');
  protected readonly defaultLength = signal<number>(120);

  protected examTypeId: number | null = null;

  protected readonly dialogVisible = signal(false);
  protected editing: Editing = this.blank();

  protected readonly examTypeFilterOptions = computed(() => [
    { label: 'All Types', value: null as number | null },
    ...this.examTypes().map((t) => ({ label: t.label ?? '', value: (t.id ?? null) as number | null })),
  ]);

  protected readonly examTypeOptions = computed(() =>
    this.examTypes().map((t) => ({ label: t.label ?? '', value: (t.id ?? null) as number | null })),
  );

  protected readonly prefOptions = computed(() =>
    this.prefLevels().map((p) => ({ label: p.name ?? '', value: (p.id ?? null) as number | null })),
  );

  constructor() {
    this.page.set('Examination Periods');
    this.reload();
  }

  private blank(): Editing {
    return { id: null, examTypeId: null, date: '', start: null, length: null, startOffset: 0, stopOffset: 0, prefLevelId: null };
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.send(
      { operation: 'LOAD', examTypeId: this.examTypeId },
      () => this.loading.set(false),
      (e) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    );
  }

  onExamTypeFilterChange(): void {
    this.reload();
  }

  private send(request: ExamPeriodEditRequest, done: () => void, fail: (e: ApiError) => void): void {
    this.rpc.execute<ExamPeriodEditResponse>('ExamPeriodEditRequest', request).subscribe({
      next: (res) => {
        this.records.set(res.records ?? []);
        this.examTypes.set(res.examTypes ?? []);
        this.prefLevels.set(res.prefLevels ?? []);
        this.editable.set(!!res.editable);
        this.addable.set(!!res.addable);
        this.deletable.set(!!res.deletable);
        this.defaultDate.set(res.defaultDate ?? '');
        this.defaultLength.set(res.defaultLength ?? 120);
        this.examTypeId = res.examTypeId ?? null;
        this.page.set(res.title || 'Examination Periods');
        done();
      },
      error: (e: ApiError) => fail(e),
    });
  }

  private neutralPref(): number | null {
    // Neutral preference (prolog "0"); the backend also defaults to it when unset.
    const n = this.prefLevels().find((p) => p.prolog === '0' || p.name?.toLowerCase() === 'neutral');
    return n?.id ?? this.prefLevels()[0]?.id ?? null;
  }

  add(): void {
    this.editing = this.blank();
    this.editing.date = this.defaultDate();
    this.editing.length = this.defaultLength();
    this.editing.examTypeId = this.examTypeId ?? this.examTypes()[0]?.id ?? null;
    this.editing.prefLevelId = this.neutralPref();
    this.dialogVisible.set(true);
  }

  edit(r: ExamPeriodRecord): void {
    if (!r.editable) return;
    this.editing = {
      id: r.id ?? null,
      examTypeId: r.examTypeId ?? null,
      date: r.date ?? '',
      start: r.start ?? null,
      length: r.length ?? null,
      startOffset: r.startOffset ?? 0,
      stopOffset: r.stopOffset ?? 0,
      prefLevelId: r.prefLevelId ?? null,
    };
    this.dialogVisible.set(true);
  }

  cancel(): void {
    this.dialogVisible.set(false);
  }

  submit(): void {
    if (!this.editing.date?.trim()) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'A date is required.' });
      return;
    }
    if (this.editing.examTypeId == null) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'An examination type is required.' });
      return;
    }
    if (this.editing.start == null || this.editing.start <= 0) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'A start time is required.' });
      return;
    }
    if (this.editing.length == null || this.editing.length <= 0) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'A length is required.' });
      return;
    }

    const record: ExamPeriodRecord = {
      id: this.editing.id,
      examTypeId: this.editing.examTypeId,
      date: this.editing.date.trim(),
      start: this.editing.start,
      length: this.editing.length,
      startOffset: this.editing.startOffset ?? 0,
      stopOffset: this.editing.stopOffset ?? 0,
      prefLevelId: this.editing.prefLevelId,
    };

    this.saving.set(true);
    this.send(
      { operation: 'SAVE', examTypeId: this.examTypeId, record },
      () => {
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.messages.add({ severity: 'success', summary: this.editing.id != null ? 'Saved' : 'Created', detail: 'Examination period saved.' });
      },
      (e) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    );
  }

  confirmDelete(r: ExamPeriodRecord): void {
    this.confirm.confirm({
      header: 'Delete examination period',
      message: `Delete the examination period on ${r.dateLabel ?? ''} at ${r.startLabel ?? ''}?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(r),
    });
  }

  private doDelete(r: ExamPeriodRecord): void {
    this.send(
      { operation: 'DELETE', examTypeId: this.examTypeId, record: { id: r.id } },
      () => this.messages.add({ severity: 'success', summary: 'Deleted', detail: 'Examination period deleted.' }),
      (e) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    );
  }
}
