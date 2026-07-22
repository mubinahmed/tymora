import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/**
 * Examination PDF Reports (legacy examPdfReport.action). Select one or more
 * reports, an output format, an examination type and the subject-area scope,
 * then Generate — the ExamPdfReportRequest command bean enqueues the report on
 * the existing asynchronous solver-server queue (the same infrastructure the
 * legacy page uses). The queue table below polls for progress and links to the
 * finished output. Gated by Right.ExaminationPdfReports. E-mail delivery of the
 * reports stays on the legacy page.
 */

type Operation = 'LOAD' | 'GENERATE' | 'REMOVE';

interface IdLabel {
  id?: number;
  label?: string;
}
interface ComboItem {
  value?: string;
  label?: string;
}
interface QueueRow {
  id?: string;
  name?: string;
  status?: string;
  progress?: string;
  owner?: string;
  session?: string;
  created?: string;
  started?: string;
  finished?: string;
  output?: string;
  outputLink?: string;
  log?: string;
  canDelete?: boolean;
}

interface ExamPdfReportRequest {
  operation: Operation;
  examTypeId?: number;
  reports?: string[];
  mode?: string;
  all?: boolean;
  subjects?: number[];
  removeId?: string;
  dispRooms?: boolean;
  dispLimit?: boolean;
  totals?: boolean;
  direct?: boolean;
  m2d?: boolean;
  btb?: boolean;
  itype?: boolean;
  classSchedule?: boolean;
  ignoreEmptyExams?: boolean;
  dispNote?: boolean;
  compact?: boolean;
  roomDispNames?: boolean;
  limit?: string;
  roomCodes?: string;
  noRoom?: string;
  since?: string;
}

interface ExamPdfReportResponse {
  title?: string;
  warning?: string;
  examTypeId?: number;
  mode?: string;
  all?: boolean;
  examTypes?: IdLabel[];
  reports?: ComboItem[];
  modes?: ComboItem[];
  subjectAreas?: IdLabel[];
  queue?: QueueRow[];
  dispRooms?: boolean;
  dispLimit?: boolean;
  totals?: boolean;
  direct?: boolean;
  m2d?: boolean;
  btb?: boolean;
  itype?: boolean;
  classSchedule?: boolean;
  ignoreEmptyExams?: boolean;
  dispNote?: boolean;
  compact?: boolean;
  roomDispNames?: boolean;
  limit?: string;
  roomCodes?: string;
  noRoom?: string;
  since?: string;
}

/** A boolean report option: [modelKey, label]. */
const OPTION_DEFS: { key: keyof Options; label: string }[] = [
  { key: 'dispRooms', label: 'Display rooms' },
  { key: 'dispLimit', label: 'Display room capacity' },
  { key: 'totals', label: 'Display totals' },
  { key: 'direct', label: 'Direct conflicts' },
  { key: 'm2d', label: 'More-than-2-a-day conflicts' },
  { key: 'btb', label: 'Back-to-back conflicts' },
  { key: 'itype', label: 'Display instructional type' },
  { key: 'classSchedule', label: 'Display class schedule' },
  { key: 'ignoreEmptyExams', label: 'Ignore empty exams' },
  { key: 'dispNote', label: 'Display note' },
  { key: 'compact', label: 'Compact report' },
  { key: 'roomDispNames', label: 'Use room display names' },
];

interface Options {
  dispRooms: boolean;
  dispLimit: boolean;
  totals: boolean;
  direct: boolean;
  m2d: boolean;
  btb: boolean;
  itype: boolean;
  classSchedule: boolean;
  ignoreEmptyExams: boolean;
  dispNote: boolean;
  compact: boolean;
  roomDispNames: boolean;
}

@Component({
  selector: 'app-exam-pdf-report',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MultiSelectModule,
    CheckboxModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './exam-pdf-report.html',
})
export class ExamPdfReport implements OnInit, OnDestroy {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly optionDefs = OPTION_DEFS;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly generating = signal(false);
  protected readonly data = signal<ExamPdfReportResponse | null>(null);

  protected examTypeId: number | null = null;
  protected selectedReports: string[] = [];
  protected mode: string | null = null;
  protected all = true;
  protected selectedSubjects: number[] = [];
  protected limit = '';
  protected roomCodes = '';
  protected noRoom = '';
  protected since = '';
  protected options: Options = {
    dispRooms: true,
    dispLimit: true,
    totals: false,
    direct: true,
    m2d: true,
    btb: false,
    itype: false,
    classSchedule: false,
    ignoreEmptyExams: false,
    dispNote: false,
    compact: false,
    roomDispNames: false,
  };

  private poll: ReturnType<typeof setInterval> | null = null;

  protected readonly examTypeOptions = computed(() =>
    (this.data()?.examTypes ?? []).map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );
  protected readonly reportOptions = computed(() =>
    (this.data()?.reports ?? []).map((r) => ({ label: r.label ?? '', value: r.value ?? '' })),
  );
  protected readonly modeOptions = computed(() =>
    (this.data()?.modes ?? []).map((m) => ({ label: m.label ?? '', value: m.value ?? '' })),
  );
  protected readonly subjectOptions = computed(() =>
    (this.data()?.subjectAreas ?? []).map((s) => ({ label: s.label ?? '', value: s.id ?? null })),
  );

  protected readonly queue = computed<QueueRow[]>(() => this.data()?.queue ?? []);

  protected readonly canGenerate = computed(
    () => this.selectedReports.length > 0 && (this.all || this.selectedSubjects.length > 0),
  );

  ngOnInit(): void {
    this.page.set('Examination PDF Reports');
    this.load(true);
  }

  ngOnDestroy(): void {
    this.stopPoll();
  }

  onExamTypeChange(): void {
    // Exam type is a generation parameter only; no reload needed.
  }

  private applyDefaults(d: ExamPdfReportResponse): void {
    this.data.set(d);
    this.examTypeId = d.examTypeId ?? null;
    this.mode = d.mode ?? this.mode;
    this.all = d.all ?? this.all;
    this.options = {
      dispRooms: !!d.dispRooms,
      dispLimit: !!d.dispLimit,
      totals: !!d.totals,
      direct: !!d.direct,
      m2d: !!d.m2d,
      btb: !!d.btb,
      itype: !!d.itype,
      classSchedule: !!d.classSchedule,
      ignoreEmptyExams: !!d.ignoreEmptyExams,
      dispNote: !!d.dispNote,
      compact: !!d.compact,
      roomDispNames: !!d.roomDispNames,
    };
    this.limit = d.limit ?? '';
    this.roomCodes = d.roomCodes ?? '';
    this.noRoom = d.noRoom ?? '';
    this.since = d.since ?? '';
    this.page.set(d.title || 'Examination PDF Reports');
    this.syncPoll();
  }

  /** Refresh only the queue (keeps the current form inputs). */
  refreshQueue(): void {
    this.rpc.execute<ExamPdfReportResponse>('ExamPdfReportRequest', { operation: 'LOAD' }).subscribe({
      next: (d) => {
        this.data.set(d);
        this.syncPoll();
      },
      error: () => {},
    });
  }

  private load(applyDefaults: boolean): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<ExamPdfReportResponse>('ExamPdfReportRequest', { operation: 'LOAD' }).subscribe({
      next: (d) => {
        if (applyDefaults) this.applyDefaults(d);
        else {
          this.data.set(d);
          this.syncPoll();
        }
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  generate(): void {
    if (!this.canGenerate()) return;
    this.generating.set(true);
    const request: ExamPdfReportRequest = {
      operation: 'GENERATE',
      reports: this.selectedReports,
      all: this.all,
      subjects: this.all ? [] : this.selectedSubjects,
      ...this.options,
      limit: this.limit,
      roomCodes: this.roomCodes,
      noRoom: this.noRoom,
      since: this.since,
    };
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    if (this.mode != null) request.mode = this.mode;
    this.rpc.execute<ExamPdfReportResponse>('ExamPdfReportRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.generating.set(false);
        this.syncPoll();
      },
      error: (e: ApiError) => {
        this.generating.set(false);
        this.error.set(e.message);
      },
    });
  }

  remove(row: QueueRow): void {
    if (!row.id) return;
    this.rpc
      .execute<ExamPdfReportResponse>('ExamPdfReportRequest', { operation: 'REMOVE', removeId: row.id })
      .subscribe({
        next: (d) => {
          this.data.set(d);
          this.syncPoll();
        },
        error: () => {},
      });
  }

  /** Poll while any queue item is still running; stop otherwise. */
  private syncPoll(): void {
    const active = this.queue().some((q) => !q.finished);
    if (active && !this.poll) {
      this.poll = setInterval(() => this.refreshQueue(), 3000);
    } else if (!active) {
      this.stopPoll();
    }
  }

  private stopPoll(): void {
    if (this.poll) {
      clearInterval(this.poll);
      this.poll = null;
    }
  }
}
