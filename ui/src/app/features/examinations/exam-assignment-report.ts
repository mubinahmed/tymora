import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';
import { ExamAssignmentDialog } from './exam-assignment-dialog';

/** An id + label pair (ExamAssignmentReportInterface.ExamTypeInfo): exam types, subject areas. */
interface IdLabel {
  id?: number;
  label?: string;
}

/** One selectable report (ExamAssignmentReportInterface.ReportInfo). */
interface ReportInfo {
  name?: string;
  label?: string;
  group?: string;
}

/** Plain-text cells, a sort rank per column, and the linked examination. */
interface ReportRow {
  examId?: number;
  cells?: string[];
  ranks?: number[];
}

interface ExamAssignmentReportResponse {
  title?: string;
  examTypeId?: number;
  examTypes?: IdLabel[];
  reports?: ReportInfo[];
  report?: string;
  subjectAreas?: IdLabel[];
  subjectAreaId?: number;
  showSections?: boolean;
  filter?: string;
  solverData?: boolean;
  nrExams?: number;
  message?: string;
  columns?: string[];
  alignments?: string[];
  rows?: ReportRow[];
}

interface ExamAssignmentReportRequest {
  examTypeId?: number | null;
  report?: string;
  subjectAreaId?: number | null;
  showSections?: boolean;
  filter?: string;
}

/** Reports a planner looks at to find clashes (offered as quick links). */
const CLASH_REPORTS = ['DirectStudentConflicts', 'BackToBackStudentConflicts', 'More2ADayStudentConflicts', 'DirectInstructorConflicts'];

/**
 * Examination Reports (legacy examAssignmentReport.action). Every report of the legacy page,
 * including the student and instructor clash reports (direct, back-to-back, more than two a day,
 * individual) and the statistics. The backend runs the legacy generators, so the figures match the
 * legacy page; columns sort with the legacy comparators. A row that refers to an examination opens
 * it in the examination assignment dialog, where the clash can be resolved. CSV export of the table.
 */
@Component({
  selector: 'app-exam-assignment-report',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    CheckboxModule,
    MessageModule,
    ProgressSpinnerModule,
    TagModule,
    TooltipModule,
    ExamAssignmentDialog,
  ],
  templateUrl: './exam-assignment-report.html',
  styleUrl: './exam-assignment-report.scss',
})
export class ExamAssignmentReport implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private route = inject(ActivatedRoute);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamAssignmentReportResponse | null>(null);

  protected examTypeId: number | null = null;
  protected report = 'ExamAssignmentReport';
  protected subjectAreaId: number | null = null;
  protected showSections = true;
  protected filter = '';

  protected readonly sortCol = signal(0);
  protected readonly sortAsc = signal(true);

  protected readonly dialogVisible = signal(false);
  protected readonly dialogExamId = signal<number | null>(null);

  protected readonly examTypeOptions = computed(() => (this.data()?.examTypes ?? []).map((t) => ({ label: t.label ?? '', value: t.id ?? null })));
  protected readonly subjectOptions = computed(() => (this.data()?.subjectAreas ?? []).map((t) => ({ label: t.label ?? '', value: t.id ?? null })));
  protected readonly reportGroups = computed(() => {
    const groups = new Map<string, { label: string; value: string }[]>();
    for (const r of this.data()?.reports ?? []) {
      const g = r.group ?? 'Reports';
      if (!groups.has(g)) groups.set(g, []);
      groups.get(g)!.push({ label: r.label ?? r.name ?? '', value: r.name ?? '' });
    }
    return Array.from(groups.entries()).map(([label, items]) => ({ label, items }));
  });
  protected readonly clashLinks = computed(() =>
    (this.data()?.reports ?? []).filter((r) => CLASH_REPORTS.includes(r.name ?? '')),
  );
  protected readonly reportLabel = computed(() => {
    const d = this.data();
    return d?.reports?.find((r) => r.name === d.report)?.label ?? d?.title ?? 'Examination Reports';
  });
  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);
  protected readonly rows = computed<ReportRow[]>(() => {
    const all = (this.data()?.rows ?? []).slice();
    const c = this.sortCol();
    const dir = this.sortAsc() ? 1 : -1;
    return all.sort((a, b) => ((a.ranks?.[c] ?? 0) - (b.ranks?.[c] ?? 0)) * dir);
  });
  protected readonly linkedRows = computed(() => this.rows().some((r) => r.examId != null));

  ngOnInit(): void {
    this.page.set('Examination Reports');
    const q = this.route.snapshot.queryParamMap;
    if (q.get('report')) this.report = q.get('report')!;
    this.load(true);
  }

  protected load(initial = false): void {
    this.loading.set(true);
    this.error.set(null);
    const req: ExamAssignmentReportRequest = initial
      ? { report: this.report }
      : { examTypeId: this.examTypeId, report: this.report, subjectAreaId: this.subjectAreaId, showSections: this.showSections, filter: this.filter };
    this.rpc.execute<ExamAssignmentReportResponse>('ExamAssignmentReportRequest', req).subscribe({
      next: (d) => {
        this.data.set(d);
        this.examTypeId = d.examTypeId ?? null;
        this.report = d.report ?? this.report;
        this.subjectAreaId = d.subjectAreaId ?? null;
        this.showSections = !!d.showSections;
        this.filter = d.filter ?? '';
        this.sortCol.set(0);
        this.sortAsc.set(true);
        this.page.set(this.reportLabel());
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  protected pickReport(name: string | undefined): void {
    if (!name) return;
    this.report = name;
    this.load();
  }

  protected sortBy(c: number): void {
    if (this.sortCol() === c) this.sortAsc.set(!this.sortAsc());
    else {
      this.sortCol.set(c);
      this.sortAsc.set(true);
    }
  }

  protected align(c: number): string {
    const a = (this.data()?.alignments?.[c] ?? 'left').toLowerCase();
    return a === 'right' || a === 'center' ? a : 'left';
  }

  protected openExam(row: ReportRow): void {
    if (row.examId == null) return;
    this.dialogExamId.set(row.examId);
    this.dialogVisible.set(true);
  }

  protected exportCsv(): void {
    downloadCsv(this.reportLabel(), this.columns(), this.rows().map((r) => (r.cells ?? []).map((c) => c ?? '')));
  }
}
