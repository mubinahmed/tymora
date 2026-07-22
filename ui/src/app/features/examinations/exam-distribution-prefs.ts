import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/**
 * Create / list / edit for the legacy examDistributionPrefs.action (Examination
 * Distribution Preferences) page. Backed by the ExamDistributionPrefsRequest
 * command bean: LOAD lists the preferences for the selected examination type
 * (optionally filtered by subject area), COURSES / EXAMS drive the
 * subject→course→exam cascade in the add/edit dialog, DETAIL loads one
 * preference, SAVE creates/updates it (distribution type + preference level +
 * the grouped examinations), DELETE removes it. Gated by
 * Right.ExaminationDistributionPreferences (plus per-preference add/edit/delete).
 */

type Operation = 'LOAD' | 'COURSES' | 'EXAMS' | 'DETAIL' | 'SAVE' | 'DELETE';

interface IdLabel {
  id?: number;
  label?: string;
}

interface DistributionTypeInfo {
  id?: number;
  label?: string;
  allowed?: string;
  description?: string;
}

interface PrefLevelInfo {
  id?: number;
  name?: string;
  char?: string;
}

interface ExamLine {
  subjectAreaId?: number | null;
  courseId?: number | null;
  examId?: number | null;
  examLabel?: string;
}

interface DistPrefRecord {
  id?: number | null;
  examTypeId?: number | null;
  distributionTypeId?: number | null;
  prefLevelId?: number | null;
  description?: string;
  exams?: ExamLine[];
}

interface DistPrefRow {
  id?: number;
  cells?: string[];
}

interface ExamDistributionPrefsRequest {
  operation: Operation;
  examTypeId?: number;
  subjectAreaId?: number;
  courseNbr?: string;
  id?: number;
  lookupSubjectAreaId?: number;
  lookupCourseId?: number;
  record?: DistPrefRecord;
}

interface ExamDistributionPrefsResponse {
  title?: string;
  examTypeId?: number;
  subjectAreaId?: number;
  courseNbr?: string;
  canAdd?: boolean;
  examTypes?: IdLabel[];
  subjectAreas?: IdLabel[];
  courses?: IdLabel[];
  exams?: IdLabel[];
  columns?: string[];
  rows?: DistPrefRow[];
  editableIds?: number[];
  deletableIds?: number[];
  record?: DistPrefRecord;
  distributionTypes?: DistributionTypeInfo[];
  prefLevels?: PrefLevelInfo[];
}

/** A row being edited in the dialog — carries its own course/exam option lists. */
interface EditLine {
  subjectAreaId: number | null;
  courseId: number | null;
  examId: number | null;
  courseOptions: { label: string; value: number | null }[];
  examOptions: { label: string; value: number | null }[];
}

@Component({
  selector: 'app-exam-distribution-prefs',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    DialogModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
    TooltipModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './exam-distribution-prefs.html',
})
export class ExamDistributionPrefs implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly data = signal<ExamDistributionPrefsResponse | null>(null);
  protected readonly filter = signal('');

  protected readonly distributionTypes = signal<DistributionTypeInfo[]>([]);
  protected readonly prefLevels = signal<PrefLevelInfo[]>([]);

  protected readonly dialogVisible = signal(false);
  protected editId: number | null = null;
  protected editTypeId: number | null = null;
  protected editLevelId: number | null = null;
  protected editLines: EditLine[] = [];

  protected examTypeId: number | null = null;
  protected subjectAreaId: number | null = null;

  protected readonly examTypeOptions = computed(() =>
    (this.data()?.examTypes ?? []).map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );

  protected readonly subjectAreaOptions = computed(() => [
    { label: 'All', value: null as number | null },
    ...(this.data()?.subjectAreas ?? []).map((s) => ({ label: s.label ?? '', value: s.id ?? null })),
  ]);

  /** Subject areas for the cascade (no "All" option). */
  protected readonly cascadeSubjectOptions = computed(() =>
    (this.data()?.subjectAreas ?? []).map((s) => ({ label: s.label ?? '', value: s.id ?? null })),
  );

  protected readonly typeOptions = computed(() =>
    this.distributionTypes().map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );

  /** Preference levels allowed by the currently selected distribution type. */
  protected readonly levelOptions = computed(() => {
    const type = this.distributionTypes().find((t) => t.id === this.editTypeId);
    const allowed = type?.allowed ?? '';
    return this.prefLevels()
      .filter((l) => !allowed || (l.char != null && allowed.indexOf(l.char) >= 0))
      .map((l) => ({ label: l.name ?? '', value: l.id ?? null }));
  });

  protected readonly typeDescription = computed(
    () => this.distributionTypes().find((t) => t.id === this.editTypeId)?.description ?? '',
  );

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly editableIds = computed(() => new Set(this.data()?.editableIds ?? []));
  protected readonly deletableIds = computed(() => new Set(this.data()?.deletableIds ?? []));

  protected readonly rows = computed<DistPrefRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Examination Distribution Preferences');
    this.load();
  }

  canEdit(row: DistPrefRow): boolean {
    return row.id != null && this.editableIds().has(row.id);
  }
  canDelete(row: DistPrefRow): boolean {
    return row.id != null && this.deletableIds().has(row.id);
  }

  onExamTypeChange(): void {
    this.load();
  }
  onSubjectAreaChange(): void {
    this.load();
  }
  reload(): void {
    this.load();
  }

  private applyList(d: ExamDistributionPrefsResponse): void {
    this.data.set(d);
    this.examTypeId = d.examTypeId ?? null;
    this.subjectAreaId = d.subjectAreaId ?? null;
    this.distributionTypes.set(d.distributionTypes ?? []);
    this.prefLevels.set(d.prefLevels ?? []);
    this.page.set(d.title || 'Examination Distribution Preferences');
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: ExamDistributionPrefsRequest = { operation: 'LOAD' };
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    if (this.subjectAreaId != null) request.subjectAreaId = this.subjectAreaId;
    this.rpc.execute<ExamDistributionPrefsResponse>('ExamDistributionPrefsRequest', request).subscribe({
      next: (d) => {
        this.applyList(d);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  // ---- add / edit dialog --------------------------------------------------

  add(): void {
    this.editId = null;
    this.editTypeId = null;
    this.editLevelId = null;
    this.editLines = [this.blankLine()];
    this.dialogVisible.set(true);
  }

  edit(row: DistPrefRow): void {
    if (row.id == null) return;
    const request: ExamDistributionPrefsRequest = { operation: 'DETAIL', id: row.id };
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    this.rpc.execute<ExamDistributionPrefsResponse>('ExamDistributionPrefsRequest', request).subscribe({
      next: (d) => {
        this.distributionTypes.set(d.distributionTypes ?? this.distributionTypes());
        this.prefLevels.set(d.prefLevels ?? this.prefLevels());
        const rec = d.record;
        if (!rec || rec.id == null) {
          this.messages.add({ severity: 'error', summary: 'Load failed', detail: 'The preference could not be loaded.' });
          return;
        }
        this.editId = rec.id;
        this.editTypeId = rec.distributionTypeId ?? null;
        this.editLevelId = rec.prefLevelId ?? null;
        this.editLines = [];
        // Build one editable line per grouped exam, prefetching its course/exam options.
        (rec.exams ?? []).forEach((ex) => {
          const line: EditLine = {
            subjectAreaId: ex.subjectAreaId ?? null,
            courseId: ex.courseId ?? null,
            examId: ex.examId ?? null,
            courseOptions: [],
            examOptions: ex.examId != null ? [{ label: ex.examLabel ?? '', value: ex.examId }] : [],
          };
          this.editLines.push(line);
          if (line.subjectAreaId != null) this.fetchCourses(line);
          if (line.courseId != null) this.fetchExams(line);
        });
        if (!this.editLines.length) this.editLines = [this.blankLine()];
        this.dialogVisible.set(true);
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message }),
    });
  }

  private blankLine(): EditLine {
    return { subjectAreaId: null, courseId: null, examId: null, courseOptions: [], examOptions: [] };
  }

  addLine(): void {
    this.editLines = [...this.editLines, this.blankLine()];
  }
  removeLine(i: number): void {
    this.editLines = this.editLines.filter((_, idx) => idx !== i);
    if (!this.editLines.length) this.editLines = [this.blankLine()];
  }

  onTypeChange(): void {
    const allowed = new Set(this.levelOptions().map((o) => o.value));
    if (!allowed.has(this.editLevelId)) this.editLevelId = null;
  }

  onLineSubjectChange(line: EditLine): void {
    line.courseId = null;
    line.examId = null;
    line.courseOptions = [];
    line.examOptions = [];
    if (line.subjectAreaId != null) this.fetchCourses(line);
  }

  onLineCourseChange(line: EditLine): void {
    line.examId = null;
    line.examOptions = [];
    if (line.courseId != null) this.fetchExams(line);
  }

  private fetchCourses(line: EditLine): void {
    const request: ExamDistributionPrefsRequest = { operation: 'COURSES', lookupSubjectAreaId: line.subjectAreaId! };
    this.rpc.execute<ExamDistributionPrefsResponse>('ExamDistributionPrefsRequest', request).subscribe({
      next: (d) => {
        line.courseOptions = (d.courses ?? []).map((c) => ({ label: c.label ?? '', value: c.id ?? null }));
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message }),
    });
  }

  private fetchExams(line: EditLine): void {
    const request: ExamDistributionPrefsRequest = { operation: 'EXAMS', lookupCourseId: line.courseId! };
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    this.rpc.execute<ExamDistributionPrefsResponse>('ExamDistributionPrefsRequest', request).subscribe({
      next: (d) => {
        line.examOptions = (d.exams ?? []).map((x) => ({ label: x.label ?? '', value: x.id ?? null }));
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message }),
    });
  }

  cancel(): void {
    this.dialogVisible.set(false);
  }

  submit(): void {
    if (this.editTypeId == null) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Distribution type is required.' });
      return;
    }
    if (this.editLevelId == null) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Preference level is required.' });
      return;
    }
    const exams: ExamLine[] = this.editLines
      .filter((l) => l.examId != null)
      .map((l) => ({ subjectAreaId: l.subjectAreaId, courseId: l.courseId, examId: l.examId }));
    if (exams.length < 2) {
      this.messages.add({
        severity: 'error',
        summary: 'Validation',
        detail: 'Select at least two examinations for the distribution preference.',
      });
      return;
    }
    const record: DistPrefRecord = {
      id: this.editId,
      distributionTypeId: this.editTypeId,
      prefLevelId: this.editLevelId,
      exams,
    };
    this.saving.set(true);
    const request: ExamDistributionPrefsRequest = { operation: 'SAVE', record };
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    if (this.subjectAreaId != null) request.subjectAreaId = this.subjectAreaId;
    this.rpc.execute<ExamDistributionPrefsResponse>('ExamDistributionPrefsRequest', request).subscribe({
      next: (d) => {
        this.applyList(d);
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Distribution preference saved.' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  confirmDelete(row: DistPrefRow): void {
    if (row.id == null) return;
    const label = (row.cells ?? []).slice(0, 2).filter((c) => c).join(' — ');
    this.confirm.confirm({
      header: 'Delete distribution preference',
      message: `Delete "${label || 'this preference'}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(row.id!),
    });
  }

  private doDelete(id: number): void {
    const request: ExamDistributionPrefsRequest = { operation: 'DELETE', id };
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    if (this.subjectAreaId != null) request.subjectAreaId = this.subjectAreaId;
    this.rpc.execute<ExamDistributionPrefsResponse>('ExamDistributionPrefsRequest', request).subscribe({
      next: (d) => {
        this.applyList(d);
        this.messages.add({ severity: 'success', summary: 'Deleted', detail: 'Distribution preference deleted.' });
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
