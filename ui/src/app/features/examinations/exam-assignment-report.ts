import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';

/** One selectable examination type (ExamAssignmentReportInterface.ExamTypeInfo). */
interface ExamTypeInfo {
  id?: number;
  label?: string;
}

/** A projected report row (SimpleListInterface.Row). */
interface ReportRow {
  id?: number;
  cells?: string[];
}

/** Response of the ExamAssignmentReportRequest command bean. */
interface ExamAssignmentReportResponse {
  title?: string;
  examTypeId?: number;
  examTypes?: ExamTypeInfo[];
  columns?: string[];
  rows?: ReportRow[];
}

/** Request payload for the ExamAssignmentReportRequest command bean. */
interface ExamAssignmentReportRequest {
  examTypeId?: number;
}

/**
 * Read-only Examination Assignment Report for the legacy examAssignmentReport.action
 * (Examination Reports) page. Pick an examination type; the backend returns the
 * committed/persisted exam assignments of that type projected to string rows
 * (Examination, Enrollment, Seating Type, Date, Time, Room, Room Capacity, Instructor).
 * Served by the new ExamAssignmentReportBackend command bean; gated by
 * Right.ExaminationReports for the current academic session. The conflict/statistics
 * sub-reports and PDF/CSV export remain on the legacy page (solver-dependent, deferred).
 */
@Component({
  selector: 'app-exam-assignment-report',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './exam-assignment-report.html',
})
export class ExamAssignmentReport implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamAssignmentReportResponse | null>(null);
  protected readonly filter = signal('');

  protected examTypeId: number | null = null;

  protected readonly examTypeOptions = computed(() =>
    (this.data()?.examTypes ?? []).map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly rows = computed<ReportRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Examination Assignment Report');
    this.load();
  }

  onExamTypeChange(): void {
    this.load();
  }

  reload(): void {
    this.load();
  }

  exportCsv(): void {
    const rows = this.rows().map((r) => (r.cells ?? []).map((c) => c ?? ''));
    downloadCsv(this.data()?.title || 'Examination Assignment Report', this.columns(), rows);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: ExamAssignmentReportRequest = {};
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    this.rpc.execute<ExamAssignmentReportResponse>('ExamAssignmentReportRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.examTypeId = d.examTypeId ?? null;
        this.page.set(d.title || 'Examination Assignment Report');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
